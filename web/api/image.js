/**
 * POST /api/image — génération d'illustrations via les modèles image Gemini.
 *
 * La clé vit uniquement dans la variable d'environnement Vercel `TOON_IA`.
 * Elle n'est jamais renvoyée au navigateur, ni journalisée, ni incluse dans une erreur.
 * GET /api/image renvoie la liste des modèles image accessibles (diagnostic, sans clé).
 */

const IMAGE_MODELS = [
  'gemini-3.1-flash-lite-image',
  'gemini-3.1-flash-image',
  'gemini-2.5-flash-image',
  'gemini-3.1-flash-image-preview',
  'gemini-3-pro-image',
  'gemini-3-pro-image-preview',
  'gemini-nano-banana-2.1'
];
const MAX_PROMPT_CHARS = 8000;
const WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 40;
const UPSTREAM_TIMEOUT_MS = 55000;

const hits = new Map();

function readApiKey() {
  return process.env.TOON_IA || process.env.GEMINI_API_KEY || '';
}

function rateLimited(ip) {
  const now = Date.now();
  const entry = hits.get(ip);
  if (!entry || now - entry.start > WINDOW_MS) {
    hits.set(ip, { start: now, count: 1 });
    return false;
  }
  entry.count += 1;
  return entry.count > MAX_REQUESTS_PER_WINDOW;
}

function extractImage(data) {
  if (!data) return null;

  // Réponse de l'API Interactions : steps[].content[] = { type: 'image', data, mime_type }.
  const steps = Array.isArray(data.steps) ? data.steps : [];
  for (const step of steps) {
    for (const block of (step && step.content) || []) {
      if (block && block.type === 'image' && block.data) {
        return { data: block.data, mimeType: block.mime_type || 'image/png' };
      }
    }
  }
  if (data.output_image && data.output_image.data) {
    return { data: data.output_image.data, mimeType: data.output_image.mime_type || 'image/png' };
  }

  // Ancien format generateContent : candidates[].content.parts[].inlineData.
  const candidate = data.candidates && data.candidates[0];
  for (const part of (candidate && candidate.content && candidate.content.parts) || []) {
    const inline = part.inlineData || part.inline_data;
    if (inline && inline.data) {
      return { data: inline.data, mimeType: inline.mimeType || inline.mime_type || 'image/png' };
    }
  }
  return null;
}

async function requestImage(key, model, prompt, aspectRatio, signal) {
  const upstream = await fetch('https://generativelanguage.googleapis.com/v1beta/interactions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-goog-api-key': key
    },
    signal,
    body: JSON.stringify({
      model,
      input: [{ type: 'text', text: prompt }],
      response_format: {
        type: 'image',
        mime_type: 'image/jpeg',
        aspect_ratio: aspectRatio,
        image_size: '1K'
      }
    })
  });
  const data = await upstream.json().catch(() => null);
  return { upstream, data };
}

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, max-age=0');

  const apiKey = readApiKey();
  if (!apiKey) {
    return res.status(503).json({
      error: {
        message:
          'La variable TOON_IA n’est pas configurée sur ce déploiement. Ajoutez-la dans Vercel → Settings → Environment Variables, puis redéployez.'
      }
    });
  }

  if (req.method === 'GET') {
    try {
      const upstream = await fetch('https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000', {
        headers: { 'x-goog-api-key': apiKey }
      });
      const data = await upstream.json().catch(() => null);
      if (!upstream.ok) {
        return res.status(upstream.status).json({
          error: { message: (data && data.error && data.error.message) || 'Impossible de lister les modèles.' }
        });
      }
      const models = ((data && data.models) || [])
        .map((m) => ({
          name: String(m.name || '').replace(/^models\//, ''),
          methods: m.supportedGenerationMethods || []
        }))
        .filter((m) => /image/i.test(m.name));
      return res.status(200).json({ models });
    } catch (error) {
      return res.status(502).json({ error: { message: 'Le proxy n’a pas pu joindre Gemini.' } });
    }
  }

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'GET, POST');
    return res.status(405).json({ error: { message: 'Méthode non autorisée.' } });
  }

  const ip =
    (req.headers['x-forwarded-for'] || '').split(',')[0].trim() ||
    (req.socket && req.socket.remoteAddress) ||
    'inconnu';
  if (rateLimited(ip)) {
    return res.status(429).json({ error: { message: 'Trop d’images demandées. Réessayez dans une minute.' } });
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (_) { body = null; }
  }
  if (!body || typeof body !== 'object') {
    return res.status(400).json({ error: { message: 'Corps JSON invalide.' } });
  }

  const prompt = body.prompt;
  if (typeof prompt !== 'string' || !prompt.trim()) {
    return res.status(400).json({ error: { message: 'Le champ "prompt" est requis.' } });
  }
  if (prompt.length > MAX_PROMPT_CHARS) {
    return res.status(413).json({ error: { message: 'Prompt trop long (limite : ' + MAX_PROMPT_CHARS + ' caractères).' } });
  }

  const aspectRatio = typeof body.aspectRatio === 'string' && /^\d{1,2}:\d{1,2}$/.test(body.aspectRatio)
    ? body.aspectRatio
    : '3:4';
  const forcedModel = typeof body.model === 'string' && IMAGE_MODELS.includes(body.model) ? body.model : null;
  const models = forcedModel ? [forcedModel] : IMAGE_MODELS;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  let last = { status: 502, message: 'Aucun modèle image n’a répondu.' };
  let freeTierBlocked = false;

  try {
    for (const model of models) {
      if (controller.signal.aborted) break;
      const { upstream, data } = await requestImage(apiKey, model, prompt, aspectRatio, controller.signal);

      if (upstream.ok) {
        const image = extractImage(data);
        if (image) {
          return res.status(200).json({
            image: 'data:' + image.mimeType + ';base64,' + image.data,
            mimeType: image.mimeType,
            model
          });
        }
        last = { status: 502, message: 'Réponse image vide du modèle ' + model + '.' };
        continue;
      }

      const message =
        (data && data.error && data.error.message) || 'Erreur Gemini (HTTP ' + upstream.status + ').';
      last = { status: upstream.status, message, model };
      if (upstream.status === 429 && /free tier|limit:\s*0/i.test(message)) freeTierBlocked = true;
      // Accès refusé : inutile d'essayer les autres modèles.
      if (upstream.status === 401 || upstream.status === 403) break;
      // 400 / 404 / 429 : le modèle n'est pas disponible ou quota à zéro pour
      // cette clé, mais un autre modèle image peut répondre.
    }

    if (freeTierBlocked) {
      return res.status(429).json({
        error: {
          message:
            'La génération d’images nécessite un projet Gemini avec facturation active. La clé TOON_IA actuelle est en Free Tier : Google autorise 0 image. Activez la facturation sur le projet lié à la clé, puis relancez « Illustrations IA ».'
        }
      });
    }
    return res.status(last.status || 502).json({ error: { message: last.message, model: last.model || null } });
  } catch (error) {
    const aborted = error && error.name === 'AbortError';
    return res.status(aborted ? 504 : 502).json({
      error: {
        message: aborted
          ? 'Gemini n’a pas généré l’image dans le délai imparti.'
          : 'Le proxy n’a pas pu joindre Gemini pour l’image.'
      }
    });
  } finally {
    clearTimeout(timer);
  }
};

module.exports.config = { maxDuration: 60 };
