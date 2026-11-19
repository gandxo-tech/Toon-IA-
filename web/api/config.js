/**
 * GET /api/config
 * Indique au client si une clé Gemini est configurée côté serveur.
 * Ne renvoie jamais la clé, ni un fragment de clé.
 */
module.exports = function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store, max-age=0');
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: { message: 'Méthode non autorisée.' } });
  }
  return res.status(200).json({
    hasServerKey: Boolean(process.env.GEMINI_API_KEY),
    model: process.env.GEMINI_MODEL || 'gemini-3.1-flash-lite-preview'
  });
};
