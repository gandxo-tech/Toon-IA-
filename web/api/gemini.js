/**
 * POST /api/gemini — proxy serveur vers l'API Gemini.
 *
 * La clé vit uniquement dans la variable d'environnement Vercel `TOON_IA`.
 * Elle n'est jamais renvoyée au navigateur, ni journalisée, ni incluse dans une erreur.
 * Modèles limités à une liste blanche pour éviter tout détournement de quota.
 */

const ALLOWED_MODELS = new Set([
  'gemini-3.1-flash-lite-preview',
  'gemini-3.5-flash',
  'gemini-2.5-flash',
  'gemini-2.5-pro',
  'gemini-2.0-flash'
]);
// Le modèle défini dans GEMINI_MODEL (Vercel) est toujours autorisé.
if (process.env.GEMINI_MODEL) ALLOWED_MODELS.add(process.env.GEMINI_MODEL);
const DEFAULT_MODEL = process.env.GEMINI_MODEL || 'gemini-3.1-flash-lite-preview';
const MAX_PROMPT_CHARS = 24000;
const WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 90;

// Limite best-effort, par instance de fonction (pas une garantie distribuée).
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

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, max-age=0');

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: { message: 'Méthode non autorisée.' } });
  }

  const apiKey = readApiKey();
  if (!apiKey) {
    return res.status(503).json({
      error: {
        message:
          'La variable TOON_IA n’est pas configurée sur ce déploiement. Ajoutez-la dans Vercel → Settings → Environment Variables, puis redéployez.'
      }
    });
  }

  const ip =
    (req.headers['x-forwarded-for'] || '').split(',')[0].trim() ||
    (req.socket && req.socket.remoteAddress) ||
    'inconnu';
  if (rateLimited(ip)) {
    return res.status(429).json({ error: { message: 'Trop de requêtes depuis cette adresse. Réessayez dans une minute.' } });
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

  const model = ALLOWED_MODELS.has(body.model) ? body.model : DEFAULT_MODEL;
  const temperature = typeof body.temperature === 'number' && body.temperature >= 0 && body.temperature <= 2 ? body.temperature : 0.9;
  const schema = body.schema && typeof body.schema === 'object' ? body.schema : undefined;

  const generationConfig = { temperature };
  if (schema) {
    generationConfig.responseMimeType = 'application/json';
    generationConfig.responseSchema = schema;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 55000);

  try {
    const upstream = await fetch(
      'https://generativelanguage.googleapis.com/v1beta/models/' +
        encodeURIComponent(model) +
        ':generateContent?key=' +
        encodeURIComponent(apiKey),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig
        })
      }
    );

    const data = await upstream.json().catch(() => null);

    if (!upstream.ok) {
      const message =
        (data && data.error && data.error.message) || 'Erreur Gemini (HTTP ' + upstream.status + ').';
      // On relaie le code et le message, jamais la clé ni l'URL complète.
      return res.status(upstream.status).json({ error: { message } });
    }

    return res.status(200).json(data);
  } catch (error) {
    const aborted = error && error.name === 'AbortError';
    return res.status(aborted ? 504 : 502).json({
      error: {
        message: aborted
          ? 'Gemini n’a pas répondu dans le délai imparti.'
          : 'Le proxy n’a pas pu joindre Gemini.'
      }
    });
  } finally {
    clearTimeout(timer);
  }
};

module.exports.config = { maxDuration: 60 };
