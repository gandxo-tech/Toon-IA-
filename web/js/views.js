/* =========================================================
   ToonIA Studio — views.js
   Rendu de chaque module du studio (HTML généré côté client).
   ========================================================= */
(function () {
  'use strict';

  const T = window.T;
  const esc = T.esc;
  const Views = (window.Views = {});
  Views.local = { directionTab: 'visual' };
  T.busy = null;
  T.lastError = null;

  /* ---------- chemins de liaison ---------- */
  T.getPath = function (obj, path) {
    return String(path).split('.').reduce((acc, key) => {
      if (acc == null) return undefined;
      if (Array.isArray(acc)) {
        const idx = /^\d+$/.test(key) ? Number(key) : acc.findIndex((x) => x && (x.id === key || String(x.orderIndex) === key));
        return idx >= 0 ? acc[idx] : undefined;
      }
      return acc[key];
    }, obj);
  };

  T.setPath = function (obj, path, value) {
    const keys = String(path).split('.');
    let cur = obj;
    for (let i = 0; i < keys.length - 1; i++) {
      const k = keys[i];
      if (Array.isArray(cur)) {
        const idx = /^\d+$/.test(k) ? Number(k) : cur.findIndex((x) => x && x.id === k);
        if (idx < 0) return;
        cur = cur[idx];
      } else {
        if (cur[k] == null) cur[k] = {};
        cur = cur[k];
      }
    }
    const last = keys[keys.length - 1];
    if (Array.isArray(cur) && !/^\d+$/.test(last)) {
      const idx = cur.findIndex((x) => x && x.id === last);
      if (idx >= 0) { cur.splice(idx, 1); return; }
    }
    cur[last] = value;
  };

  /* ---------- helpers de rendu ---------- */
  const badge = (done, labelDone, labelDraft) =>
    '<span class="badge ' + (done ? 'badge-canon' : 'badge-draft') + '">' + (done ? '● ' + (labelDone || 'Validé') : '○ ' + (labelDraft || 'Brouillon')) + '</span>';

  const bind = (path, kind) => 'data-bind="' + esc(path) + '" data-kind="' + (kind || 'text') + '"';

  const input = (path, value, label, placeholder, kind) =>
    '<label class="field"><span>' + esc(label) + '</span>' +
    (kind === 'area'
      ? '<textarea class="textarea" ' + bind(path, 'area') + ' placeholder="' + esc(placeholder || '') + '">' + esc(value || '') + '</textarea>'
      : '<input class="input" type="text" ' + bind(path, 'text') + ' value="' + esc(value || '') + '" placeholder="' + esc(placeholder || '') + '" />') +
    '</label>';

  const area = (path, value, label, rows) =>
    '<label class="field"><span>' + esc(label) + '</span><textarea class="textarea" ' + bind(path, 'area') + (rows ? ' style="min-height:' + rows + 'px"' : '') + '>' + esc(value || '') + '</textarea></label>';

  const busyBlock = (label) =>
    '<div class="alert alert-info"><span class="alert-ico"><span class="spinner"></span></span><div><h4>' + esc(label) + '</h4><p>Le moteur structure la réponse. Vous pouvez continuer à lire, la génération se termine automatiquement.</p></div></div>';

  const errorBlock = () => {
    if (!T.lastError) return '';
    return '<div class="alert alert-danger"><span class="alert-ico">⚠</span><div style="flex:1"><h4>La génération a échoué</h4><p>' + esc(T.lastError) + '</p>' +
      '<div class="row" style="margin-top:10px"><button class="btn btn-sm" data-action="retry-last">Réessayer</button><button class="btn btn-sm btn-ghost" data-action="clear-error">Fermer</button></div></div></div>';
  };

  const empty = (icon, title, text, actionHtml) =>
    '<div class="empty"><div class="empty-ico">' + icon + '</div><h3>' + esc(title) + '</h3><p class="small" style="max-width:46ch;margin:0 auto 16px">' + esc(text) + '</p>' + (actionHtml || '') + '</div>';

  const head = (eyebrow, title, lead, actions) =>
    '<div class="page-head"><div class="grow"><div class="eyebrow">' + esc(eyebrow) + '</div><h1>' + esc(title) + '</h1>' +
    (lead ? '<p class="lead">' + lead + '</p>' : '') + '</div>' + (actions ? '<div class="row">' + actions + '</div>' : '') + '</div>';

  const genBtn = (action, label, disabled) =>
    '<button class="btn btn-primary" data-action="' + action + '"' + (disabled ? ' disabled' : '') + '>✦ ' + label + '</button>';

  function canonStats(p) {
    const c = T.canon.counts(p);
    return '<div class="grid grid-4" style="margin-bottom:18px">' +
      '<div class="metric"><b>' + c.characters + '</b><span>personnages canon</span></div>' +
      '<div class="metric"><b>' + c.locations + '</b><span>lieux canon</span></div>' +
      '<div class="metric"><b>' + c.chapters + '</b><span>chapitres canon</span></div>' +
      '<div class="metric"><b>' + c.memories + '</b><span>faits mémorisés</span></div>' +
      '</div>';
  }

  /* ---------- brief unique ---------- */
  const BRIEF_MIN = 40;

  const fmtTime = (secs) => {
    const s = Math.max(0, Math.round(secs || 0));
    return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  };

  function jobStepsHtml(job) {
    const steps = (job && job.steps) || [];
    return '<ol class="job-steps">' + steps.map((s) => {
      const state = s.state === 'done' ? 'done' : s.state === 'active' ? (job && job.state === 'error' ? 'failed' : 'active') : 'pending';
      const ico = state === 'done' ? '✓' : state === 'failed' ? '✕' : state === 'active' ? '<span class="spinner" style="width:10px;height:10px;border-width:1.5px"></span>' : '·';
      return '<li class="job-step is-' + state + '"><span class="dot">' + ico + '</span><span>' + esc(s.label) + '</span></li>';
    }).join('') + '</ol>';
  }

  // Panneau de génération partagé par la carte du brief et le dock flottant.
  Views.jobPanel = function (job, opts) {
    if (!job) return '';
    const dock = !!(opts && opts.dock);
    const failed = job.state === 'error';
    const folded = !!(dock && !failed && T.ui && T.ui.jobFolded);
    const steps = job.steps || [];
    const done = steps.filter((s) => s.state === 'done').length;
    const pct = Math.round((done / Math.max(steps.length, 1)) * 100);
    const secs = ((job.endedAt || Date.now()) - (job.startedAt || Date.now())) / 1000;
    return '<section class="job-card' + (failed ? ' is-error' : '') + '" role="status" aria-live="' + (failed ? 'assertive' : 'polite') + '">' +
      '<div class="job-head"><span class="job-live' + (failed ? ' is-error' : '') + '">' +
        (failed ? '⚠ Génération interrompue' : '<span class="job-pulse"></span>Génération') + '</span>' +
        '<span class="job-timer">' + fmtTime(secs) + '</span>' +
        (dock && !failed ? '<button type="button" class="job-fold" data-action="toggle-job" aria-label="' + (T.ui && T.ui.jobFolded ? 'Déplier' : 'Réduire') + '">' + (T.ui && T.ui.jobFolded ? '+' : '−') + '</button>' : '') +
      '</div>' +
      '<div class="job-now">' + esc(job.label || 'Préparation…') + '</div>' +
      (failed
        ? '<div class="job-error">' + esc(job.error || 'La génération s’est arrêtée avant la fin.') + '</div>'
        : (job.detail ? '<div class="job-detail">' + esc(job.detail) + '</div>' : '')) +
      '<div class="job-bar"><span style="width:' + pct + '%"></span></div>' +
      '<div class="job-meta"><span>' + done + ' / ' + Math.max(steps.length, 1) + ' phases</span><span>' + pct + ' %</span></div>' +
      (folded ? '' : jobStepsHtml(job)) +
      (failed
        ? '<div class="job-actions">' +
            '<button type="button" class="btn btn-primary btn-sm" data-action="retry-last">↻ Réessayer</button>' +
            '<button type="button" class="btn btn-ghost btn-sm" data-action="' + (dock ? 'dismiss-job' : 'clear-error') + '">' + (dock ? 'Masquer' : 'Fermer') + '</button>' +
          '</div>'
        : '') +
      '</section>';
  };

  const BRIEF_EXAMPLE = 'Ex : Un thriller psychologique dans une ville portuaire de 1990. Sujet : une inspectrice en disgrâce enquête sur trois disparitions que sa hiérarchie veut enterrer. Personnages, ton, ambiances, palette, chapitres, contraintes… écrivez tout ici.';

  function briefCard(p, opts) {
    const compact = !!(opts && opts.compact);
    const job = T.job;
    const ready = T.ai.hasServerKey();
    const missing = T.ai.server.checked && !ready;
    const chapters = Math.min(3, Math.max(1, Number(T.state.settings.chapters) || 1));
    const words = p.idea ? p.idea.trim().split(/\s+/).filter(Boolean).length : 0;
    const len = String(p.idea || '').trim().length;
    const tooShort = len < BRIEF_MIN;
    const presets = T.briefPresets || [];
    const counter = len === 0
      ? 'Décrivez votre webtoon pour activer la génération'
      : (tooShort ? 'Encore ' + (BRIEF_MIN - len) + ' caractères minimum' : words + ' mot' + (words > 1 ? 's' : '') + ' · chapitre 1 entièrement découpé');
    return '<div class="card brief-card"' + (opts && opts.top ? ' style="margin-top:18px"' : '') + '>' +
      '<div class="row-between" style="align-items:flex-start"><div>' +
        '<div class="eyebrow">Brief unique</div>' +
        '<h2 style="margin:4px 0 6px">' + (compact ? 'Affiner le brief' : 'Votre gros prompt') + '</h2>' +
        '<p class="small" style="margin:0;max-width:78ch">' + (compact
          ? 'Relancez la génération complète après avoir modifié le brief. Les étapes déjà validées du canon sont conservées.'
          : 'Collez votre brief complet. Un seul bouton enchaîne analyse, personnages, univers, histoire, direction artistique, chapitres, panels, bulles et couverture.') + '</p>' +
      '</div><span class="badge ' + (ready ? 'badge-canon' : 'badge-ai') + '">' + esc(T.ai.engineLabel()) + '</span></div>' +
      '<label class="field" style="margin-top:14px"><span>Brief / prompt</span>' +
        '<textarea class="textarea' + (compact ? '' : ' textarea-lg') + '" style="min-height:' + (compact ? 120 : 190) + 'px" data-bind="idea" data-kind="area" placeholder="' + esc(BRIEF_EXAMPLE) + '">' + esc(p.idea) + '</textarea></label>' +
      (presets.length && !compact
        ? '<div class="brief-examples"><span class="tiny">Amorces rapides :</span>' + presets.map((pr) =>
            '<button type="button" class="chip chip-add" data-action="brief-preset" data-preset="' + esc(pr.id) + '">' + esc(pr.label) + '</button>').join('') + '</div>'
        : '') +
      '<div class="brief-foot" style="margin-top:16px">' +
        '<div class="brief-meta">' +
          '<label class="field" style="margin:0;min-width:150px"><span>Chapitres à écrire</span><select class="select" data-bind="settings.chapters" data-kind="select">' +
            [1, 2, 3].map((n) => '<option value="' + n + '"' + (chapters === n ? ' selected' : '') + '>' + n + ' chapitre' + (n > 1 ? 's' : '') + '</option>').join('') + '</select></label>' +
          '<span class="tiny' + (tooShort && len > 0 ? ' is-warn' : '') + '">' + esc(counter) + '</span>' +
        '</div>' +
        '<button type="button" class="btn btn-primary btn-lg" data-action="gen-all"' + (missing || tooShort ? ' disabled' : '') + '>✦ Générer tout le projet</button>' +
      '</div>' +
      '<p class="tiny" style="margin:10px 0 0">Astuce <span class="mono">⌘ / Ctrl + Entrée</span> lance la génération. Comptez une vingtaine d’appels Gemini selon le nombre de chapitres.</p>' +
      (missing
        ? '<div class="alert alert-warn" style="margin-top:12px"><span class="alert-ico">⚡</span><div><h4>TOON_IA absente de ce déploiement</h4><p>Ajoutez la variable <span class="mono">TOON_IA</span> dans Vercel (Production) puis redéployez : sans elle, aucune génération n’est possible.</p></div></div>'
        : '') +
      '<div id="job-inline-host">' + (job ? Views.jobPanel(job) : (T.lastError ? errorBlock() : '')) + '</div>' +
      '</div>';
  }

  /* =========================================================
     ACCUEIL
     ========================================================= */
  Views.accueil = function (p) {
    const progress = T.progress(p);
    const doneCount = progress.filter((x) => x.done).length;
    const st = T.stats(p);
    const started = !!(p.analysis || (p.chapters && p.chapters.length) || String(p.idea || '').trim());
    const next = progress.find((x) => !x.done) || progress[progress.length - 1];
    const synopsis = (p.marketing && p.marketing.selectedSynopsis) || (p.story && (p.story.logline || p.story.premisse)) || p.idea || '';
    const guide = '<div class="guide">' + [
      ['01', 'Collez votre brief', 'Une prémisse, un ton, des personnages, une fin — même en vrac.'],
      ['02', 'Générez tout', 'Bibles, chapitres, storyboard, panels et bulles s’enchaînent sans intervention.'],
      ['03', 'Relisez, validez, exportez', 'Chaque validation entre dans le canon ; les contradictions sont signalées, jamais corrigées en silence.']
    ].map((g) => '<div class="guide-step"><div class="guide-num">' + g[0] + '</div><div><h4>' + esc(g[1]) + '</h4><p>' + esc(g[2]) + '</p></div></div>').join('') + '</div>';
    const modules = [
      ['projet', 'Idée & genèse', 'Concept, analyse IA, validation'],
      ['personnages', 'Personnages', 'Character Bible, 12 champs'],
      ['univers', 'Univers & lieux', 'World Bible, factions, objets'],
      ['histoire', 'Histoire & arc', 'Story Bible, timeline'],
      ['direction', 'Direction art & humour', 'Visual Bible, Humor DNA'],
      ['chapitre', 'Chapitres & scènes', 'Scripts structurés'],
      ['storyboard', 'Storyboard', 'Rythme et arc émotionnel'],
      ['panels', 'Panels', 'Cadrage, composition, prompts'],
      ['dialogues', 'Dialogues', 'Bulles typées et placées'],
      ['lettering', 'Lettering', 'Rendu et édition des bulles'],
      ['couverture', 'Couverture', 'Titres, synopsis, visuel'],
      ['canon', 'Canon Memory', 'Faits, contradictions, versionnage'],
      ['lecture', 'Lecture verticale', 'Assemblage webtoon'],
      ['export', 'Export', 'JSON, Markdown, PDF']
    ];

    return head('Studio de webtoon', 'Une idée. Un canon. Un webtoon.', 'Transformez une prémisse en récit structuré : bibles narratives, personnages cohérents, chapitres, panels, dialogues et export.', '<button type="button" class="btn" data-action="new-project">+ Nouveau webtoon</button>') +
      (!started
        ? briefCard(p) + guide
        : '<div class="hero">' +
            '<div class="eyebrow">Projet actif · ' + doneCount + '/9 étapes</div>' +
            '<h1>' + esc(p.title) + '</h1>' +
            '<p>' + esc(T.clamp(synopsis || 'Votre projet est prêt à démarrer.', 280)) + '</p>' +
            '<div class="row">' +
              '<button type="button" class="btn btn-primary" data-action="go" data-route="' + next.key + '">' + (doneCount >= progress.length ? 'Ouvrir le résultat' : 'Continuer : ' + esc(next.label)) + ' →</button>' +
              '<button type="button" class="btn" data-action="go" data-route="lecture">Lire le webtoon</button>' +
              '<button type="button" class="btn btn-ghost" data-action="go" data-route="export">Exporter</button>' +
              '<span class="badge ' + (T.ai.hasServerKey() ? 'badge-canon' : 'badge-ai') + '">' + esc(T.ai.engineLabel()) + '</span>' +
            '</div>' +
            '<div class="pipeline">' + progress.map((x) =>
              '<button type="button" class="pipe-step ' + (x.done ? 'is-done' : '') + '" data-action="go" data-route="' + x.key + '"><b>' + (x.done ? 'FAIT' : 'ÉTAPE') + '</b>' + esc(x.label) + '</button>').join('') +
            '</div>' +
          '</div>' +
          briefCard(p, { compact: true, top: true })) +

      '<div class="grid grid-4" style="margin-top:18px">' +
        '<div class="metric"><b>' + st.characters + '</b><span>personnages</span></div>' +
        '<div class="metric"><b>' + st.chapters + '</b><span>chapitres</span></div>' +
        '<div class="metric"><b>' + st.panels + '</b><span>panels</span></div>' +
        '<div class="metric"><b>' + st.dialogues + '</b><span>bulles</span></div>' +
      '</div>' +

      '<div class="row-between" style="margin:26px 0 14px"><div><h2 style="margin:0">Modules du studio</h2><p class="small" style="margin:2px 0 0">Chaque module alimente le canon validé du projet.</p></div>' +
      '<button class="btn btn-ghost btn-sm" data-action="go" data-route="canon">Voir le canon →</button></div>' +
      '<div class="modules">' + modules.map((m, i) => {
        const step = progress.find((x) => x.key === m[0]);
        return '<button class="module" data-action="go" data-route="' + m[0] + '">' +
          '<span class="module-num">' + String(i + 1).padStart(2, '0') + '</span>' +
          '<span class="module-title">' + esc(m[1]) + '</span>' +
          '<span class="module-desc">' + esc(m[2]) + '</span>' +
          '<span class="module-state">' + (step ? (step.done ? '<span class="badge badge-canon">● prêt</span>' : '<span class="badge badge-draft">○ à faire</span>') : '<span class="badge">outil</span>') + '</span>' +
          '</button>';
      }).join('') + '</div>' +

      '';
  };

  /* =========================================================
     IDÉE & GENÈSE
     ========================================================= */
  Views.projet = function (p) {
    const adv = p.advanced || {};
    const a = p.analysis;
    const names = ['genre', 'ambiance', 'epoque', 'personnagesSouhaites', 'inspirations', 'longueurVisee', 'publicCible', 'contraintes', 'evenementsSouhaites'];
    const labels = {
      genre: 'Genre souhaité', ambiance: 'Ambiance', epoque: 'Époque / contexte', personnagesSouhaites: 'Personnages souhaités',
      inspirations: 'Inspirations', longueurVisee: 'Longueur visée', publicCible: 'Public cible', contraintes: 'Contraintes narratives', evenementsSouhaites: 'Événements souhaités'
    };

    return head('Étape 1 — Analyse', 'Idée & genèse', 'Décrivez librement. Le moteur extrait la structure, signale les ambiguïtés — et ne complète jamais ce que vous n’avez pas dit.', '') +
      errorBlock() +
      (T.busy === 'analyse' ? busyBlock('Analyse de l’idée en cours…') : '') +

      '<div class="grid grid-2"><div>' +
        '<div class="card">' +
          '<label class="field"><span>Titre de travail</span><input class="input" ' + bind('title', 'text') + ' value="' + esc(p.title) + '" /></label>' +
          '<div class="row" style="margin-bottom:12px"><span class="tiny">Exemples de test rapide :</span>' +
            '<button class="btn btn-sm btn-ghost" data-action="preset-vague">Idée vague</button>' +
            '<button class="btn btn-sm btn-ghost" data-action="preset-detailed">Idée détaillée</button></div>' +
          '<label class="field"><span>Votre idée de webtoon *</span>' +
            '<textarea class="textarea textarea-lg" ' + bind('idea', 'area') + ' placeholder="Ex : Dans une métropole sous-marine isolée du monde, une cartographe découvre que le dôme commence à se fissurer depuis l’intérieur...">' + esc(p.idea) + '</textarea></label>' +
          '<div class="row-between"><span class="tiny">' + (p.idea ? p.idea.split(/\s+/).filter(Boolean).length + ' mots' : '0 mot') + '</span>' +
            genBtn('analyze', a ? 'Relancer l’analyse' : 'Analyser l’idée', !p.idea.trim()) + '</div>' +
        '</div>' +

        '<details class="card" style="margin-top:16px"' + (Object.keys(adv).some((k) => adv[k]) ? ' open' : '') + '>' +
          '<summary style="cursor:pointer;font-weight:700">⚙ Détails avancés <span class="small" style="font-weight:400">(facultatifs — réduisent les ambiguïtés)</span></summary>' +
          '<div class="grid grid-2" style="margin-top:14px">' +
            names.map((k) => '<label class="field"><span>' + esc(labels[k]) + '</span><input class="input" ' + bind('advanced.' + k, 'text') + ' value="' + esc(adv[k] || '') + '" /></label>').join('') +
          '</div>' +
        '</details>' +
      '</div><div>' +

      '<div class="card">' +
        '<div class="row-between" style="margin-bottom:10px"><h3 style="margin:0">Résultat de l’analyse</h3>' + (a ? badge(true, 'Modifiable') : badge(false, 'En attente')) + '</div>' +
        (!a
          ? empty('✦', 'Aucune analyse', 'Collez votre brief puis lancez l’analyse : Gemini en extrait le genre, le ton, les thèmes et les points à clarifier.')
          : '<div class="stack">' +
              '<div class="grid grid-2">' +
                '<label class="field"><span>Genre principal</span><input class="input" ' + bind('analysis.genrePrincipal', 'text') + ' value="' + esc(a.genrePrincipal) + '" /></label>' +
                '<label class="field"><span>Ton narratif</span><input class="input" ' + bind('analysis.ton', 'text') + ' value="' + esc(a.ton) + '" /></label>' +
              '</div>' +
              '<div><div class="label">Sous-genres</div><div class="chips">' + (a.sousGenres || []).map((s) =>
                '<span class="chip">' + esc(s) + '<button data-action="remove-sub" data-value="' + esc(s) + '" aria-label="Retirer">×</button></span>').join('') +
                '<button class="chip chip-add" data-action="add-sub">+ ajouter</button></div></div>' +
              '<div><div class="label">Thèmes détectés</div><div class="chips">' + (a.themesDetectes || []).map((s) =>
                '<span class="chip">' + esc(s) + '<button data-action="remove-theme" data-value="' + esc(s) + '" aria-label="Retirer">×</button></span>').join('') +
                '<button class="chip chip-add" data-action="add-theme">+ ajouter</button></div></div>' +
              (a.elementsNarratifsExplicites && a.elementsNarratifsExplicites.length
                ? '<div class="card-flat"><h3>💡 Éléments explicitement posés</h3>' + a.elementsNarratifsExplicites.map((x) => '<p class="small" style="margin:0 0 4px">• ' + esc(x) + '</p>').join('') + '</div>'
                : '') +
              (a.ambiguitesAClarifier && a.ambiguitesAClarifier.length
                ? '<div class="card-flat" style="border-color:rgba(251,191,36,.35)"><h3>❓ Ambiguïtés à clarifier</h3>' +
                  a.ambiguitesAClarifier.map((x, i) => '<p class="small" style="margin:0 0 5px">' + (i + 1) + '. ' + esc(x) + '</p>').join('') +
                  '<p class="tiny" style="margin:8px 0 0">Ces points ne sont pas remplis automatiquement : complétez l’idée ou les détails avancés, puis relancez.</p></div>'
                : '') +
              '<button class="btn btn-primary btn-block" data-action="validate-analysis">Valider l’idée et passer aux personnages →</button>' +
            '</div>') +
      '</div></div></div>';
  };

  /* =========================================================
     PERSONNAGES
     ========================================================= */
  Views.personnages = function (p) {
    const chars = p.characters || [];
    return head('Étape 2 — Character Bible', 'Personnages', 'Chaque fiche décrit rôle, personnalité, apparence, capacités, relations, arc et état initial. La couleur des cheveux et des yeux est vérifiée par le Canon Memory.', genBtn('gen-characters', chars.length ? 'Régénérer' : 'Générer les personnages', !p.idea.trim())) +
      errorBlock() + (T.busy === 'characters' ? busyBlock('Construction de la Character Bible…') : '') +
      (chars.length ? canonStats(p) : '') +
      (chars.length === 0
        ? empty('☺', 'Aucun personnage', 'Lancez la génération pour obtenir 3 à 5 personnages structurés, ou créez-les à la main après une première génération.', genBtn('gen-characters', 'Générer la bible', !p.idea.trim()))
        : '<div class="grid grid-2">' + chars.map((c) => {
          const isCanon = (p.canon.characters || []).some((x) => x.name.toLowerCase() === c.name.toLowerCase());
          return '<article class="card">' +
            '<div class="row-between" style="align-items:flex-start"><div><div class="small">' + esc(c.role) + '</div><h3 style="margin:2px 0 0;font-size:20px">' + esc(c.name) + '</h3></div>' + badge(isCanon, 'Canon', 'Brouillon') + '</div>' +
            '<div class="divider"></div>' +
            area('characters.' + c.id + '.personality', c.personality, 'Personnalité') +
            area('characters.' + c.id + '.physicalAppearance', c.physicalAppearance, 'Apparence physique') +
            '<details><summary class="small" style="cursor:pointer;color:var(--violet)">Tous les champs</summary>' +
              '<div style="margin-top:10px">' +
                input('characters.' + c.id + '.role', c.role, 'Rôle') +
                input('characters.' + c.id + '.archetype', c.archetype, 'Archétype') +
                area('characters.' + c.id + '.distinctiveFeatures', c.distinctiveFeatures, 'Traits distinctifs', 60) +
                area('characters.' + c.id + '.characteristicClothing', c.characteristicClothing, 'Vêtements caractéristiques', 60) +
                area('characters.' + c.id + '.abilities', c.abilities, 'Capacités', 60) +
                area('characters.' + c.id + '.relationships', c.relationships, 'Relations', 60) +
                area('characters.' + c.id + '.plannedArc', c.plannedArc, 'Arc prévu', 60) +
                area('characters.' + c.id + '.currentState', c.currentState, 'État initial', 60) +
              '</div></details>' +
            '<div class="row" style="margin-top:12px">' +
              (isCanon
                ? '<span class="badge badge-canon">Validé dans le canon</span>'
                : '<button class="btn btn-sm btn-primary" data-action="validate-character" data-id="' + c.id + '">Valider au canon</button>') +
              '<button class="btn btn-sm btn-ghost" data-action="go" data-route="univers">Étape suivante →</button>' +
            '</div></article>';
        }).join('') + '</div>');
  };

  /* =========================================================
     UNIVERS
     ========================================================= */
  Views.univers = function (p) {
    const w = p.world;
    return head('Étape 3 — World Bible', 'Univers & lieux', 'Règles applicables, factions, géographie, structure sociale, lore, terminologie, contraintes. Les lieux validés deviennent des décors canoniques.', genBtn('gen-world', w ? 'Régénérer' : 'Générer l’univers', !p.idea.trim())) +
      errorBlock() + (T.busy === 'world' ? busyBlock('Construction de la World Bible…') : '') +
      (!w
        ? empty('◎', 'Aucun univers', 'L’univers sera généré à partir de l’idée, des personnages et du canon déjà validé.')
        : '<div class="grid grid-2"><div class="card">' +
            '<div class="row-between"><h3 style="margin:0">Fondations du monde</h3>' + badge(w.isValidated, 'Canon', 'Brouillon') + '</div>' +
            area('world.rules', w.rules, 'Règles du monde') +
            area('world.factions', w.factions, 'Factions / groupes') +
            area('world.geography', w.geography, 'Géographie') +
            area('world.socialStructure', w.socialStructure, 'Structure sociale') +
            area('world.historyLore', w.historyLore, 'Histoire & lore') +
            area('world.terminology', w.terminology, 'Terminologie') +
            area('world.worldConstraints', w.worldConstraints, 'Contraintes du monde') +
          '</div><div>' +
            '<div class="card"><div class="row-between"><h3 style="margin:0">Lieux</h3><span class="badge">' + (w.locations || []).length + '</span></div>' +
              (w.locations || []).map((l) => {
                const isCanon = (p.canon.locations || []).some((x) => x.id === l.id);
                return '<div class="card-flat" style="margin-top:10px">' +
                  '<div class="row-between"><input class="input-inline" style="font-weight:700" ' + bind('world.locations.' + l.id + '.name', 'text') + ' value="' + esc(l.name) + '" />' + badge(isCanon, 'Canon', '—') + '</div>' +
                  area('world.locations.' + l.id + '.description', l.description, 'Description', 60) +
                  input('world.locations.' + l.id + '.atmosphere', l.atmosphere, 'Atmosphère') +
                  (isCanon ? '' : '<button class="btn btn-sm" data-action="validate-location" data-id="' + l.id + '">Valider ce lieu</button>') +
                '</div>';
              }).join('') + '</div>' +
            '<div class="card" style="margin-top:16px"><h3>Objets narratifs</h3>' +
              (w.objects || []).map((o) => '<div class="card-flat" style="margin-top:10px">' +
                '<input class="input-inline" style="font-weight:700" ' + bind('world.objects.' + o.id + '.name', 'text') + ' value="' + esc(o.name) + '" />' +
                area('world.objects.' + o.id + '.significance', o.significance, 'Rôle narratif', 54) +
                area('world.objects.' + o.id + '.description', o.description, 'Description', 54) +
              '</div>').join('') + '</div>' +
            '<div class="card" style="margin-top:16px"><div class="row-between"><span class="small">Valider l’univers entier ajoute les lieux au canon et fixe les règles.</span>' +
              '<button class="btn btn-primary" data-action="validate-world">Valider l’univers</button></div></div>' +
          '</div></div>');
  };

  /* =========================================================
     HISTOIRE
     ========================================================= */
  Views.histoire = function (p) {
    const s = p.story;
    return head('Étape 4 — Story Bible', 'Histoire & arc', 'Prémisse, thèmes, conflits à trois niveaux, intrigue en trois actes, sous-intrigues, direction et rythme — plus les événements fondateurs.', genBtn('gen-story', s ? 'Régénérer' : 'Générer l’histoire', !p.idea.trim())) +
      errorBlock() + (T.busy === 'story' ? busyBlock('Construction de la Story Bible…') : '') +
      (!s
        ? empty('▤', 'Aucune histoire', 'La Story Bible s’appuie sur l’idée, les personnages et l’univers déjà validés.')
        : '<div class="grid grid-2"><div class="card">' +
            '<div class="row-between"><h3 style="margin:0">Structure narrative</h3>' + badge(s.isValidated, 'Canon', 'Brouillon') + '</div>' +
            area('story.premise', s.premise, 'Prémisse') +
            '<div class="grid grid-2">' + input('story.confirmedGenre', s.confirmedGenre, 'Genre confirmé') + input('story.generalTone', s.generalTone, 'Ton général') + '</div>' +
            area('story.mainThemes', s.mainThemes, 'Thèmes principaux', 60) +
            area('story.mainConflicts', s.mainConflicts, 'Conflits (interne / relationnel / sociétal)', 100) +
            area('story.mainPlot', s.mainPlot, 'Intrigue principale (3 actes)', 120) +
            area('story.subPlots', s.subPlots, 'Sous-intrigues', 80) +
            area('story.narrativeDirection', s.narrativeDirection, 'Direction & promesse finale', 80) +
            area('story.pacing', s.pacing, 'Rythme', 60) +
          '</div><div>' +
            '<div class="card"><h3>Timeline fondatrice</h3>' +
              (s.timelineEvents || []).map((e) => '<div class="card-flat" style="margin-top:10px">' +
                '<div class="row" style="gap:8px"><span class="badge">' + esc(e.dateOrEpoch || '—') + '</span><input class="input-inline" style="font-weight:700" ' + bind('story.timelineEvents.' + e.id + '.title', 'text') + ' value="' + esc(e.title) + '" /></div>' +
                '<textarea class="textarea" style="min-height:60px;margin-top:6px" ' + bind('story.timelineEvents.' + e.id + '.description', 'area') + '>' + esc(e.description) + '</textarea>' +
              '</div>').join('') +
            '</div>' +
            '<div class="card" style="margin-top:16px"><div class="row-between"><span class="small">Valider fixe la prémisse, les thèmes et la timeline dans le canon.</span><button class="btn btn-primary" data-action="validate-story">Valider l’histoire</button></div></div>' +
            canonStats(p) +
          '</div></div>');
  };

  /* =========================================================
     DIRECTION ARTISTIQUE & HUMOUR
     ========================================================= */
  Views.direction = function (p) {
    const tab = Views.local.directionTab;
    const v = p.visual, h = p.humor;
    const tabs = '<div class="tabs">' +
      '<button class="tab ' + (tab === 'visual' ? 'is-active' : '') + '" data-action="direction-tab" data-tab="visual">Direction artistique</button>' +
      '<button class="tab ' + (tab === 'humor' ? 'is-active' : '') + '" data-action="direction-tab" data-tab="humor">Humor DNA</button></div>';

    let body = '';
    if (tab === 'visual') {
      body = !v ? empty('✦', 'Aucune direction artistique', 'Définissez le style graphique, la palette, le découpage et les règles de continuité visuelle.')
        : '<div class="card"><div class="row-between"><h3 style="margin:0">Visual Bible</h3>' + badge(v.isValidated, 'Canon', 'Brouillon') + '</div>' +
          '<div class="grid grid-2">' +
            input('visual.artStyle', v.artStyle, 'Style graphique') +
            input('visual.colorPalette', v.colorPalette, 'Palette dominante') +
            input('visual.panelingStyle', v.panelingStyle, 'Style de découpage') +
            input('visual.lightingMood', v.lightingMood, 'Ambiance lumineuse') +
            area('visual.recurringVisualElements', v.recurringVisualElements, 'Éléments visuels récurrents', 70) +
            area('visual.framingRules', v.framingRules, 'Règles de cadrage', 70) +
            area('visual.continuityElements', v.continuityElements, 'Continuité entre chapitres', 70) +
          '</div>' +
          '<div class="row" style="margin-top:12px"><button class="btn btn-primary" data-action="validate-visual">Valider la direction artistique</button></div></div>';
    } else {
      body = !h ? empty('☺', 'Aucun Humor DNA', 'Le registre comique doit rester compatible avec le ton de la Story Bible : le Canon Memory signale toute dissonance flagrante.')
        : '<div class="card"><div class="row-between"><h3 style="margin:0">Humor DNA</h3>' + badge(h.isValidated, 'Canon', 'Brouillon') + '</div>' +
          '<div class="grid grid-2">' +
            input('humor.humorStyle', h.humorStyle, 'Registre comique') +
            input('humor.punchlineFrequency', h.punchlineFrequency, 'Fréquence des chutes') +
            area('humor.runningGags', h.runningGags, 'Running gags', 70) +
            area('humor.characterHumorHooks', h.characterHumorHooks, 'Hooks propres aux personnages', 90) +
            area('humor.comicSituations', h.comicSituations, 'Situations comiques', 70) +
          '</div>' +
          '<label class="row" style="margin-top:8px;gap:8px"><input type="checkbox" ' + (h.chibiMomentsAllowed ? 'checked' : '') + ' data-action="toggle-chibi" /> <span class="small">Inserts chibi autorisés</span></label>' +
          '<div class="row" style="margin-top:12px"><button class="btn btn-primary" data-action="validate-humor">Valider l’Humor DNA</button></div></div>';
    }

    return head('Étape 5 — Direction', 'Direction art & humour', 'Un webtoon se reconnaît à sa palette et à son rythme comique. Les deux sont versionnés dans le canon.', genBtn('gen-visual', (v && h) ? 'Régénérer' : 'Générer la direction', !p.idea.trim())) +
      errorBlock() + (T.busy === 'visual' ? busyBlock('Construction de la direction artistique…') : '') + tabs + body +
      '<div class="row" style="margin-top:20px"><button class="btn btn-ghost" data-action="go" data-route="histoire">← Histoire</button><button class="btn" data-action="go" data-route="chapitre">Chapitres →</button></div>';
  };

  /* =========================================================
     CHAPITRES
     ========================================================= */
  Views.chapitre = function (p) {
    const chapters = p.chapters || [];
    const nextNum = chapters.reduce((m, c) => Math.max(m, Number(c.number) || 0), 0) + 1;
    return head('Étape 6 — Scripts', 'Chapitres & scènes', 'Un chapitre = un synopsis et 3 à 5 scènes structurées (lieu, personnages, résumé, beats). Le découpage en panels vient après.', genBtn('gen-chapter', chapters.length ? 'Générer le chapitre ' + nextNum : 'Générer le chapitre 1', !p.idea.trim()) + (chapters.length ? ' <button class="btn" data-action="validate-chapter" data-number="' + chapters[chapters.length - 1].number + '">Valider le dernier</button>' : '')) +
      errorBlock() + (T.busy === 'chapter' ? busyBlock('Écriture du chapitre…') : '') +
      (chapters.length === 0
        ? empty('❐', 'Aucun chapitre', 'Le script doit respecter la Story Bible et les personnages validés. Les scènes utilisent les lieux canoniques.')
        : chapters.map((ch) => {
          const isCanon = (p.canon.chapters || []).some((x) => x.id === ch.id);
          return '<div class="card"><div class="row-between"><div><div class="eyebrow">Chapitre ' + esc(ch.number) + '</div><h2 style="margin:0">' + esc(ch.title) + '</h2></div>' + badge(isCanon, 'Canon', 'Brouillon') + '</div>' +
            '<p class="small" style="margin-top:6px">' + esc(ch.synopsis) + '</p>' +
            '<div class="grid grid-3" style="margin-top:14px">' + (ch.scenes || []).map((s) =>
              '<div class="card-flat"><div class="row-between"><span class="badge">Scène ' + esc(s.orderIndex) + '</span><span class="tiny">' + (s.panels || []).length + ' panels</span></div>' +
                '<h3 style="margin:8px 0 4px">' + esc(s.title) + '</h3>' +
                '<p class="tiny" style="margin:0 0 6px">📍 ' + esc(s.locationName || '—') + '</p>' +
                '<p class="small">' + esc(T.clamp(s.summary, 160)) + '</p>' +
                '<div class="row" style="gap:6px"><button class="btn btn-sm btn-ghost" data-action="open-scene" data-scene="' + s.id + '">Storyboard →</button>' +
                '<button class="btn btn-sm btn-ghost" data-action="open-panels" data-scene="' + s.id + '">Panels</button></div>' +
              '</div>').join('') + '</div></div>';
        }).join(''));
  };

  /* =========================================================
     STORYBOARD
     ========================================================= */
  function allScenes(p) {
    return (p.chapters || []).flatMap((ch) => (ch.scenes || []).map((s) => ({ chapter: ch, scene: s })));
  }
  Views.scenePicker = function (p, selectedId) {
    const list = allScenes(p);
    if (!list.length) return '';
    return '<div class="row" style="margin-bottom:16px"><span class="tiny">Scène :</span><select class="select" style="max-width:420px" data-action="scene-select">' +
      list.map((x) => '<option value="' + x.scene.id + '"' + (x.scene.id === selectedId ? ' selected' : '') + '>Ch. ' + x.chapter.number + ' · ' + esc(x.scene.title) + '</option>').join('') +
      '</select></div>';
  };

  Views.storyboard = function (p) {
    const list = allScenes(p);
    if (!list.length) {
      return head('Étape 7 — Storyboard', 'Storyboard', 'Enrichissez chaque scène avant de la découper en panels.', '') +
        empty('▥', 'Aucune scène', 'Générez d’abord un chapitre pour obtenir des scènes.', '<button class="btn btn-primary" data-action="go" data-route="chapitre">Aller aux chapitres</button>');
    }
    const sel = list.find((x) => x.scene.id === T.ui.sceneId) || list[0];
    const s = sel.scene, d = s.detail;
    return head('Étape 7 — Storyboard', 'Storyboard', 'Rythme, arc émotionnel, micro-beats, ambiance sensorielle : la scène est prête à être découpée.', genBtn('gen-detail', d ? 'Régénérer' : 'Enrichir la scène', false)) +
      errorBlock() + (T.busy === 'detail' ? busyBlock('Enrichissement de la scène…') : '') +
      Views.scenePicker(p, s.id) +
      '<div class="grid grid-2"><div class="card"><div class="eyebrow">Scène source</div><h2 style="margin:0 0 6px">' + esc(s.title) + '</h2>' +
        '<p class="small">📍 ' + esc(s.locationName) + ' · 👥 ' + esc((s.charactersPresent || []).join(', ') || '—') + '</p>' +
        '<p>' + esc(s.summary) + '</p>' +
        '<div class="label">Beats d’origine</div>' + (s.beats || []).map((b, i) => '<p class="small" style="margin:0 0 4px">' + (i + 1) + '. ' + esc(b) + '</p>').join('') +
      '</div><div class="card">' +
        (!d ? empty('▥', 'Scène non enrichie', 'L’enrichissement ne réécrit pas la scène : il précise le rythme et l’intention de mise en scène.')
          : '<div class="row-between"><h3 style="margin:0">Détail de mise en scène</h3><span class="badge badge-ai">Prêt pour le découpage</span></div>' +
            area('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.detail.enrichedSummary', d.enrichedSummary, 'Résumé enrichi', 90) +
            area('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.detail.emotionalArc', d.emotionalArc, 'Arc émotionnel', 50) +
            area('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.detail.pacingNotes', d.pacingNotes, 'Notes de rythme', 60) +
            area('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.detail.atmosphereAndSensory', d.atmosphereAndSensory, 'Ambiance & sensoriel', 60) +
            area('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.detail.characterInteractions', d.characterInteractions, 'Interactions', 60) +
            '<div class="card-flat"><div class="label">Micro-beats détaillés</div>' + (d.detailedNarrativeBeats || []).map((b, i) => '<p class="small" style="margin:0 0 4px">' + (i + 1) + '. ' + esc(b) + '</p>').join('') + '</div>' +
            '<button class="btn btn-primary btn-block" style="margin-top:14px" data-action="open-panels" data-scene="' + s.id + '">Découper en panels →</button>') +
      '</div></div>';
  };

  /* =========================================================
     PANELS
     ========================================================= */
  Views.panels = function (p) {
    const list = allScenes(p);
    if (!list.length) {
      return head('Étape 8 — Panels', 'Panels', 'Le découpage produit des cases prêtes à illustrer.', '') +
        empty('▧', 'Aucune scène', 'Créez un chapitre avant de découper des panels.', '<button class="btn btn-primary" data-action="go" data-route="chapitre">Aller aux chapitres</button>');
    }
    const sel = list.find((x) => x.scene.id === T.ui.sceneId) || list.find((x) => (x.scene.panels || []).length) || list[0];
    const s = sel.scene;
    const genreId = ((p.analysis && p.analysis._genreId) || T.profile(p).id);
    return head('Étape 8 — Panels', 'Panels', 'Caméra, composition, éclairage, arrière-plan, effets, prompt visuel autonome et continuité — chaque case est documentée.', genBtn('gen-panels', (s.panels || []).length ? 'Redécouper' : 'Découper en panels', false)) +
      errorBlock() + (T.busy === 'panels' ? busyBlock('Découpage de la scène en panels…') : '') +
      Views.scenePicker(p, s.id) +
      ((s.panels || []).length === 0
        ? empty('▧', 'Scène non découpée', '« ' + s.title + ' » n’a pas encore de panels. Le prompt visuel est en anglais et interdit explicitement tout texte dans l’image.', genBtn('gen-panels', 'Découper maintenant'))
        : '<div class="panel-grid">' + s.panels.map((pn, i) =>
            '<article class="panel-card"><div class="panel-art"><img src="' + T.art(pn.artSeed || (s.id + ':' + i), genreId) + '" alt="Visuel procédural du panel ' + esc(pn.orderIndex) + '" loading="lazy" />' +
              (pn.dialogues || []).slice(0, 2).map((d) => '<div class="bubble bubble-' + esc(d.type.toLowerCase()) + ' pos-' + (d.placement || 'TOP_LEFT').toLowerCase().replace('_', '-').replace('_', '-') + '">' + esc(T.clamp(d.text, 70)) + '</div>').join('') +
            '</div><div class="panel-body">' +
              '<div class="panel-meta"><span class="badge">#' + esc(pn.orderIndex) + '</span><span class="badge">' + esc(pn.cameraAngle) + '</span><span class="badge">' + esc(pn.dominantEmotion) + '</span>' + (pn.soundEffects ? '<span class="badge badge-ai">' + esc(pn.soundEffects) + '</span>' : '') + '</div>' +
              '<p class="small" style="margin-bottom:8px">' + esc(pn.actionDescription) + '</p>' +
              '<dl class="kv"><dt>Lieu</dt><dd>' + esc(pn.location) + '</dd><dt>Composition</dt><dd>' + esc(pn.composition) + '</dd><dt>Lumière</dt><dd>' + esc(pn.lightingAndAtmosphere) + '</dd><dt>Décor</dt><dd>' + esc(pn.backgroundDetails) + '</dd><dt>Effets</dt><dd>' + esc(pn.visualEffects) + '</dd><dt>Continuité</dt><dd>' + esc(pn.continuityNotes) + '</dd></dl>' +
              '<details style="margin-top:10px"><summary class="small" style="cursor:pointer;color:var(--violet)">Prompt visuel (EN, sans texte)</summary>' +
                '<textarea class="textarea" style="min-height:110px;margin-top:8px;font-family:var(--font-mono);font-size:11.5px" ' + bind('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.panels.' + pn.id + '.visualPrompt', 'area') + '>' + esc(pn.visualPrompt) + '</textarea>' +
                '<button class="btn btn-sm btn-ghost" style="margin-top:6px" data-action="copy" data-copy="' + esc(pn.visualPrompt) + '">Copier le prompt</button></details>' +
              '<div class="row" style="margin-top:12px;gap:6px"><button class="btn btn-sm btn-primary" data-action="gen-dialogues" data-panel="' + pn.id + '">Générer les bulles</button>' +
              '<button class="btn btn-sm btn-ghost" data-action="open-lettering">Lettering →</button></div>' +
            '</div></article>').join('') + '</div>');
  };

  /* =========================================================
     DIALOGUES
     ========================================================= */
  function sceneOfPanel(p, panelId) {
    for (const ch of p.chapters || []) for (const s of ch.scenes || []) for (const pn of s.panels || []) if (pn.id === panelId) return { ch, s, pn };
    return null;
  }

  Views.dialogues = function (p) {
    const list = allScenes(p).filter((x) => (x.scene.panels || []).length);
    if (!list.length) {
      return head('Étape 9 — Dialogues', 'Dialogues', 'Des bulles typées, placées, émotionnellement justes.', '') +
        empty('❝', 'Aucun panel', 'Générez d’abord des panels : les dialogues se rattachent toujours à une case.', '<button class="btn btn-primary" data-action="go" data-route="panels">Aller aux panels</button>');
    }
    const sel = list.find((x) => x.scene.id === T.ui.sceneId) || list[0];
    const s = sel.scene;
    const genreId = ((p.analysis && p.analysis._genreId) || T.profile(p).id);
    return head('Étape 9 — Dialogues', 'Dialogues', 'Chaque bulle a un type (réplique, pensée, cri, murmure, narration), une émotion et un placement. Aucun texte n’est incrusté dans l’image.', '<button class="btn" data-action="go" data-route="lettering">Lettering →</button>') +
      errorBlock() + (T.busy === 'dialogues' ? busyBlock('Écriture des dialogues…') : '') +
      Views.scenePicker(p, s.id) +
      '<div class="panel-grid">' + s.panels.map((pn, i) =>
        '<article class="panel-card"><div class="panel-art"><img src="' + T.art(pn.artSeed || (s.id + ':' + i), genreId) + '" alt="Panel ' + esc(pn.orderIndex) + '" loading="lazy" />' +
          (pn.dialogues || []).map((d) => '<div class="bubble bubble-' + esc(d.type.toLowerCase()) + ' pos-' + (d.placement || 'TOP_LEFT').toLowerCase().replace(/_/g, '-') + '">' + esc(T.clamp(d.text, 80)) + '</div>').join('') +
        '</div><div class="panel-body">' +
          '<div class="row-between" style="margin-bottom:8px"><span class="badge">Panel #' + esc(pn.orderIndex) + '</span><span class="tiny">' + (pn.dialogues || []).length + ' bulle(s)</span></div>' +
          '<p class="tiny" style="margin-bottom:10px">' + esc(T.clamp(pn.actionDescription, 120)) + '</p>' +
          (pn.dialogues || []).map((d) =>
            '<div class="card-flat" style="margin-bottom:8px">' +
              '<div class="row" style="gap:6px;margin-bottom:6px">' +
                '<input class="input-inline" style="width:110px;font-size:12px;font-weight:700" ' + bind('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.speakerName', 'text') + ' value="' + esc(d.speakerName) + '" />' +
                '<select class="select" style="width:auto;font-size:11px;padding:4px 6px" data-bind="chapters.' + sel.chapter.id + '.scenes.' + s.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.type" data-kind="select">' +
                  ['SPEECH', 'THOUGHT', 'SHOUT', 'WHISPER', 'NARRATION'].map((t) => '<option' + (d.type === t ? ' selected' : '') + '>' + t + '</option>').join('') + '</select>' +
                '<select class="select" style="width:auto;font-size:11px;padding:4px 6px" data-bind="chapters.' + sel.chapter.id + '.scenes.' + s.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.placement" data-kind="select">' +
                  ['TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER'].map((t) => '<option' + (d.placement === t ? ' selected' : '') + '>' + t + '</option>').join('') + '</select>' +
                '<button class="btn btn-sm btn-ghost" data-action="del-dialogue" data-panel="' + pn.id + '" data-dialogue="' + d.id + '" style="margin-left:auto">×</button>' +
              '</div>' +
              '<textarea class="textarea" style="min-height:54px;font-size:12.5px" ' + bind('chapters.' + sel.chapter.id + '.scenes.' + s.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.text', 'area') + '>' + esc(d.text) + '</textarea>' +
            '</div>').join('') +
          '<div class="row" style="gap:6px"><button class="btn btn-sm btn-primary" data-action="gen-dialogues" data-panel="' + pn.id + '">' + ((pn.dialogues || []).length ? 'Régénérer' : 'Générer') + '</button>' +
          '<button class="btn btn-sm btn-ghost" data-action="add-dialogue" data-panel="' + pn.id + '">+ bulle</button></div>' +
        '</div></article>').join('') + '</div>';
  };

  /* =========================================================
     LETTERING
     ========================================================= */
  Views.lettering = function (p) {
    const list = allScenes(p).filter((x) => (x.scene.panels || []).length);
    if (!list.length) {
      return head('Étape 10 — Lettering', 'Lettering & bulles', 'Le texte est rendu par l’application, jamais gravé dans l’illustration.', '') +
        empty('✍', 'Rien à letterer', 'Les bulles se posent sur les panels générés.', '<button class="btn btn-primary" data-action="go" data-route="panels">Aller aux panels</button>');
    }
    const sel = list.find((x) => x.scene.id === T.ui.sceneId) || list[0];
    const genreId = ((p.analysis && p.analysis._genreId) || T.profile(p).id);
    return head('Étape 10 — Lettering', 'Lettering & bulles', 'Rendu natif : réplique, pensée, cri, murmure, narration. Modifiez le texte et le placement sans jamais régénérer l’image.', '<button class="btn" data-action="go" data-route="lecture">Lecture verticale →</button>') +
      Views.scenePicker(p, sel.scene.id) +
      '<div class="reader">' + sel.scene.panels.map((pn, i) =>
        '<div class="reader-panel"><img src="' + T.art(pn.artSeed || (sel.scene.id + ':' + i), genreId) + '" alt="Panel ' + esc(pn.orderIndex) + '" />' +
          (pn.dialogues || []).map((d) => '<div class="bubble bubble-' + esc(d.type.toLowerCase()) + ' pos-' + (d.placement || 'TOP_LEFT').toLowerCase().replace(/_/g, '-') + '">' + esc(d.text) + '</div>').join('') +
        '</div>').join('') + '</div>' +
      '<div class="card" style="margin-top:18px"><h3>Réglages de lettering</h3>' +
        sel.scene.panels.flatMap((pn) => (pn.dialogues || []).map((d) =>
          '<div class="card-flat" style="margin-bottom:8px"><div class="row-between"><span class="tiny">Panel #' + esc(pn.orderIndex) + ' · ' + esc(d.speakerName) + '</span>' +
          '<div class="row" style="gap:4px"><select class="select" style="width:auto;font-size:11px;padding:4px 6px" data-bind="chapters.' + sel.chapter.id + '.scenes.' + sel.scene.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.type" data-kind="select">' +
            ['SPEECH', 'THOUGHT', 'SHOUT', 'WHISPER', 'NARRATION'].map((t) => '<option' + (d.type === t ? ' selected' : '') + '>' + t + '</option>').join('') + '</select>' +
          '<select class="select" style="width:auto;font-size:11px;padding:4px 6px" data-bind="chapters.' + sel.chapter.id + '.scenes.' + sel.scene.id + '.panels.' + pn.id + '.dialogues.' + d.id + '.placement" data-kind="select">' +
            ['TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER'].map((t) => '<option' + (d.placement === t ? ' selected' : '') + '>' + t + '</option>').join('') + '</select></div></div>' +
          '<p class="small" style="margin:6px 0 0">' + esc(d.text) + '</p></div>').join('')) +
      '</div>';
  };

  /* =========================================================
     COUVERTURE
     ========================================================= */
  Views.couverture = function (p) {
    const m = p.marketing;
    const title = (m && (m.selectedTitle || (m.titleProposals || [])[0])) || p.title;
    const tagline = (m && m.selectedTagline) || '';
    const genreId = ((p.analysis && p.analysis._genreId) || T.profile(p).id);
    const coverDesc = (m && m.selectedCoverDescription) || (m && m.coverProposal && m.coverProposal.detailedDescriptionNoText) || '';
    return head('Étape 11 — Packaging', 'Couverture', 'Titres, synopsis, accroche et description visuelle. Règle absolue : l’image ne contient aucun texte, le titre est superposé par l’application.', genBtn('gen-marketing', m ? 'Régénérer' : 'Générer le packaging', !p.idea.trim())) +
      errorBlock() + (T.busy === 'marketing' ? busyBlock('Génération du packaging éditorial…') : '') +
      (!m
        ? empty('▣', 'Aucun packaging', 'Générez plusieurs propositions de titre, trois longueurs de synopsis et une description de couverture sans texte.')
        : '<div class="cover-stage"><div class="todo-cover">' +
            '<div class="cover"><img src="' + T.art((p.id || 'cover') + ':cover', genreId) + '" alt="Couverture procédurale" />' +
              '<div class="cover-overlay"><div class="cover-title">' + esc(title) + '</div>' +
              (tagline ? '<div class="cover-tag">' + esc(tagline) + '</div>' : '') +
              '<div class="cover-author">TOONIA STUDIO · GANDXO TECH</div></div></div>' +
            '<p class="tiny" style="margin-top:8px">Aperçu procédural — le titre est une surcouche HTML, jamais incrusté dans l’image.</p>' +
          '</div><div class="stack">' +
            '<div class="card"><h3>Propositions de titre</h3><div class="chips">' + (m.titleProposals || []).map((t) =>
              '<button class="chip ' + (t === m.selectedTitle ? 'chip-neutral' : '') + '" data-action="select-title" data-value="' + esc(t) + '">' + esc(t) + '</button>').join('') + '</div>' +
              '<label class="field" style="margin-top:12px"><span>Titre retenu</span><input class="input" ' + bind('marketing.selectedTitle', 'text') + ' value="' + esc(m.selectedTitle || '') + '" /></label>' +
              '<label class="field"><span>Accroche</span><input class="input" ' + bind('marketing.selectedTagline', 'text') + ' value="' + esc(tagline) + '" /></label>' +
            '</div>' +
            '<div class="card"><h3>Synopsis</h3>' + (m.synopsisVariants || []).map((v) =>
              '<div class="card-flat" style="margin-bottom:8px"><div class="row-between"><span class="badge">' + esc(v.lengthType) + '</span>' +
              '<button class="btn btn-sm btn-ghost" data-action="select-synopsis" data-value="' + esc(v.text) + '">Choisir</button></div>' +
              '<p class="small" style="margin:8px 0 0;white-space:pre-line">' + esc(v.text) + '</p></div>').join('') +
              '<label class="field"><span>Synopsis retenu</span><textarea class="textarea" ' + bind('marketing.selectedSynopsis', 'area') + '>' + esc(m.selectedSynopsis || '') + '</textarea></label>' +
            '</div>' +
            '<div class="card"><h3>Description de couverture (sans texte)</h3>' +
              '<p class="small" style="white-space:pre-line">' + esc(coverDesc) + '</p>' +
              (m.coverProposal ? '<dl class="kv" style="margin-top:10px"><dt>Composition</dt><dd>' + esc(m.coverProposal.composition) + '</dd><dt>Palette</dt><dd>' + esc(m.coverProposal.colorPalette) + '</dd><dt>Ambiance</dt><dd>' + esc(m.coverProposal.atmosphereAndLighting) + '</dd></dl>' : '') +
              '<div class="row" style="margin-top:12px"><button class="btn btn-primary" data-action="validate-cover">Valider la couverture</button>' +
              '<button class="btn btn-ghost" data-action="copy" data-copy="' + esc(coverDesc) + '">Copier la description</button></div>' +
            '</div></div></div>');
  };

  /* =========================================================
     CANON MEMORY
     ========================================================= */
  Views.canon = function (p) {
    const tab = T.ui.canonTab || 'overview';
    const c = p.canon;
    const tabs = '<div class="tabs">' +
      [['overview', 'Vue d’ensemble'], ['characters', 'Personnages'], ['locations', 'Lieux & univers'], ['tests', 'Tests de contradiction']]
        .map(([k, l]) => '<button class="tab ' + (tab === k ? 'is-active' : '') + '" data-action="canon-tab" data-tab="' + k + '">' + l + '</button>').join('') + '</div>';

    let body = '';
    if (tab === 'overview') {
      body = canonStats(p) +
        '<div class="card"><h3>État actuel du canon</h3><p class="small">Le moteur conserve uniquement les éléments explicitement validés. Une contradiction n’est jamais corrigée en silence : elle est signalée et attend une décision.</p>' +
          '<div class="grid grid-3" style="margin-top:14px">' +
            '<div class="card-flat"><div class="tiny">Bible personnages</div><b>' + (p.characters || []).length + '</b> fiches générées</div>' +
            '<div class="card-flat"><div class="tiny">Packaging</div><b>' + (p.marketing ? 'Oui' : 'Non') + '</b> ' + (c.project && c.project.finalTitle ? '· « ' + esc(c.project.finalTitle) + ' »' : '') + '</div>' +
            '<div class="card-flat"><div class="tiny">Version</div><b>' + (c.memories || []).length + '</b> faits mémorisés</div>' +
          '</div>' +
          (c.project || c.cover
            ? '<div class="divider"></div><div class="label">Packaging validé</div>' +
              (c.project && c.project.finalTitle ? '<p style="margin:0 0 4px"><b>' + esc(c.project.finalTitle) + '</b></p>' : '') +
              (c.project && c.project.finalSynopsis ? '<p class="small" style="white-space:pre-line">' + esc(T.clamp(c.project.finalSynopsis, 320)) + '</p>' : '') +
              (c.cover && c.cover.visualDescription ? '<p class="tiny">Couverture : ' + esc(T.clamp(c.cover.visualDescription, 200)) + '</p>' : '')
            : '') +
        '</div>' +
        '<div class="card" style="margin-top:16px"><div class="row-between"><h3 style="margin:0">Mémoires & règles absolues</h3><button class="btn btn-sm" data-action="add-memory">+ Ajouter un fait</button></div>' +
          ((c.memories || []).length
            ? (c.memories || []).map((m) => '<div class="memory"><div class="cat">' + esc(m.category) + '</div>' + esc(m.fact) +
                ' <button class="btn btn-sm btn-ghost" style="float:right" data-action="del-memory" data-id="' + m.id + '">×</button></div>').join('')
            : '<p class="small">Aucune mémoire canonique pour l’instant. Validez un personnage, un lieu ou un chapitre pour en créer.</p>') +
        '</div>';
    } else if (tab === 'characters') {
      body = (c.characters || []).length
        ? '<div class="grid grid-2">' + c.characters.map((ch) =>
            '<div class="card"><div class="row-between"><div><div class="small">' + esc(ch.role) + '</div><h3 style="margin:2px 0 0">' + esc(ch.name) + '</h3></div><span class="badge badge-canon">● canon</span></div>' +
              '<p class="small" style="margin-top:8px">' + esc(ch.personality) + '</p>' +
              '<p class="tiny">' + esc(ch.physicalAppearance) + '</p></div>').join('') + '</div>'
        : empty('☺', 'Aucun personnage canonique', 'Validez des personnages depuis le module Personnages.');
    } else if (tab === 'locations') {
      body = (c.locations || []).length
        ? '<div class="grid grid-2">' + c.locations.map((l) =>
            '<div class="card"><h3>' + esc(l.name) + '</h3><p class="small">' + esc(l.description) + '</p><p class="tiny" style="color:var(--violet)">' + esc(l.atmosphere) + '</p></div>').join('') + '</div>'
        : empty('◎', 'Aucun lieu canonique', 'Validez des lieux depuis le module Univers.');
    } else {
      body = '<div class="alert alert-info"><span class="alert-ico">◉</span><div><h4>Bac à sable canonique</h4><p>Reproduisez les scénarios de test de l’application Android : établissez un fait, puis tentez de le contredire. Le moteur signale la contradiction sans jamais la corriger.</p></div></div>' +
        '<div class="grid grid-2" style="margin-top:16px"><div class="card"><h3>Étape 1 — Établir le canon</h3>' +
          '<p class="small">Insère un personnage de référence (Aiden, cheveux noirs, yeux bleus, Protagoniste) puis tente de le redéfinir.</p>' +
          '<div class="row"><button class="btn btn-sm" data-action="test-seed">Insérer Aiden au canon</button>' +
          '<button class="btn btn-sm btn-ghost" data-action="test-memory">Ajouter « Aiden meurt au chapitre 3 »</button></div>' +
          '<div class="divider"></div><h3>Étape 2 — Provoquer une contradiction</h3>' +
          '<div class="row"><button class="btn btn-sm btn-ghost" data-action="test-role">Rôle inversé</button>' +
          '<button class="btn btn-sm btn-ghost" data-action="test-hair">Cheveux roux</button>' +
          '<button class="btn btn-sm btn-ghost" data-action="test-death">Aiden réintroduit</button>' +
          '<button class="btn btn-sm btn-ghost" data-action="test-cover">Couverture avec texte</button></div>' +
          '<p class="tiny" style="margin-top:10px">Chaque test produit un rapport de contradiction détaillé, avec valeur canonique et valeur conflictuelle.</p>' +
        '</div><div class="card"><h3>Rapport de contradiction</h3>' +
          ((T.ui.contradictions || []).length
            ? T.ui.contradictions.map((x) =>
              '<div class="alert ' + (x.severity === 'HIGH' ? 'alert-danger' : 'alert-warn') + '"><span class="alert-ico">' + (x.severity === 'HIGH' ? '⛔' : '⚠') + '</span><div><h4>' + esc(x.targetType) + ' · ' + esc(x.targetName) + ' — ' + esc(x.fieldName) + '</h4>' +
                '<p>' + esc(x.description) + '</p>' +
                '<p class="tiny" style="margin-top:6px">Canon : « ' + esc(T.clamp(x.canonicalValue, 140)) + ' » · Conflit : « ' + esc(T.clamp(x.conflictingValue, 140)) + ' »</p></div></div>').join('')
            : '<p class="small">Aucune contradiction détectée pour l’instant. Lancez un test.</p>') +
        '</div></div>';
    }

    return head('Canon Memory', 'Le canon ne se corrige pas en silence.', 'Éléments validés, règles absolues, détection de contradictions. C’est le cœur du moteur narratif.', '') + tabs + body;
  };

  /* =========================================================
     LECTURE VERTICALE
     ========================================================= */
  Views.lecture = function (p) {
    const chapters = (p.chapters || []).filter((ch) => (ch.scenes || []).some((s) => (s.panels || []).length));
    if (!chapters.length) {
      return head('Lecture', 'Lecture verticale', 'Assemblage des panels validés en flux continu, format webtoon mobile.', '') +
        empty('☰', 'Rien à lire', 'Générez des panels et des dialogues pour assembler le chapitre.', '<button class="btn btn-primary" data-action="go" data-route="panels">Aller aux panels</button>');
    }
    const genreId = ((p.analysis && p.analysis._genreId) || T.profile(p).id);
    return head('Lecture', 'Lecture verticale', 'Flux continu, bulles posées, aucune coupure de page. C’est le format de diffusion cible.', '<button class="btn btn-ghost" data-action="print">Imprimer / PDF</button>') +
      chapters.map((ch) =>
        '<div style="text-align:center;margin:26px 0 14px"><div class="eyebrow" style="justify-content:center">Chapitre ' + esc(ch.number) + '</div><h2 style="margin:0">' + esc(ch.title) + '</h2></div>' +
        '<div class="reader">' + ch.scenes.flatMap((s) => s.panels || []).map((pn, i) =>
          '<div class="reader-panel"><img src="' + T.art(pn.artSeed || (ch.id + ':' + i), genreId) + '" alt="Panel ' + esc(pn.orderIndex) + '" loading="lazy" />' +
            (pn.dialogues || []).map((d) => '<div class="bubble bubble-' + esc(d.type.toLowerCase()) + ' pos-' + (d.placement || 'TOP_LEFT').toLowerCase().replace(/_/g, '-') + '">' + esc(d.text) + '</div>').join('') +
          '</div>').join('') + '</div>').join('') +
      '<p class="tiny" style="text-align:center;margin-top:18px">Visuels procéduraux (SVG) — remplacez-les par vos illustrations générées.</p>';
  };

  /* =========================================================
     EXPORT
     ========================================================= */
  Views.export = function (p) {
    const st = T.stats(p);
    return head('Diffusion', 'Export', 'JSON pour l’archivage, Markdown pour la relecture, impression navigateur pour le PDF.', '<button class="btn btn-ghost" data-action="print">Imprimer / PDF</button>') +
      '<div class="grid grid-4" style="margin-bottom:18px">' +
        '<div class="metric"><b>' + st.chapters + '</b><span>chapitres</span></div>' +
        '<div class="metric"><b>' + st.scenes + '</b><span>scènes</span></div>' +
        '<div class="metric"><b>' + st.panels + '</b><span>panels</span></div>' +
        '<div class="metric"><b>' + st.dialogues + '</b><span>bulles</span></div>' +
      '</div>' +
      '<div class="grid grid-3">' +
        '<div class="card"><h3>Projet complet (JSON)</h3><p class="small">Tout l’état du projet : bibles, canon, chapitres, panels, dialogues, packaging. Réimportable.</p><button class="btn btn-primary" data-action="export-json">Télécharger le JSON</button></div>' +
        '<div class="card"><h3>Scénario (Markdown)</h3><p class="small">Document lisible : synopsis, personnages, décors, scènes, panels et dialogues.</p><button class="btn" data-action="export-md">Télécharger le Markdown</button></div>' +
        '<div class="card"><h3>PDF (impression)</h3><p class="small">Mise en page dédiée : couverture, bibles, chapitres et planches. Choisissez « Enregistrer au format PDF ».</p><button class="btn" data-action="print">Imprimer le projet</button></div>' +
      '</div>' +
      '<div class="card" style="margin-top:16px"><h3>Aperçu du document</h3>' +
        '<div class="print-page"><h1>' + esc(p.marketing && p.marketing.selectedTitle ? p.marketing.selectedTitle : p.title) + '</h1>' +
        '<p class="lead">' + esc(p.marketing ? p.marketing.selectedSynopsis : p.idea) + '</p>' +
        (p.marketing && p.marketing.selectedTagline ? '<p><i>' + esc(p.marketing.selectedTagline) + '</i></p>' : '') +
        '<div class="divider"></div><h3>Personnages</h3>' +
        ((p.canon.characters || []).length ? p.canon.characters.map((c) => '<p class="small"><b>' + esc(c.name) + '</b> — ' + esc(c.role) + ' · ' + esc(T.clamp(c.personality, 150)) + '</p>').join('') : '<p class="small">Aucun personnage validé.</p>') +
        '<h3>Chapitres</h3>' +
        ((p.chapters || []).length ? p.chapters.map((ch) => '<div style="margin-bottom:12px"><b>Chapitre ' + esc(ch.number) + ' — ' + esc(ch.title) + '</b><p class="small">' + esc(ch.synopsis) + '</p>' +
          (ch.scenes || []).map((s) => '<p class="tiny">Scène ' + esc(s.orderIndex) + ' · ' + esc(s.title) + ' — ' + esc(s.summary) + '</p>').join('') + '</div>').join('') : '<p class="small">Aucun chapitre.</p>') +
        '</div></div>';
  };

  /* =========================================================
     RÉGLAGES
     ========================================================= */
  Views.reglages = function () {
    const s = T.state.settings;
    const ok = T.ai.hasServerKey();
    return head('Moteur', 'Moteur Gemini', 'Le site passe par le proxy serveur <span class="mono">/api/gemini</span>. La clé est lue dans la variable d’environnement Vercel <span class="mono">TOON_IA</span> : elle n’est jamais envoyée au navigateur, ni stockée ici.', '<button class="btn" data-action="check-engine">Revérifier le moteur</button>') +
      '<div class="grid grid-2"><div>' +
        '<div class="card"><h3>Clé serveur</h3>' +
          (ok
            ? '<div class="alert alert-ok" style="margin-bottom:12px"><span class="alert-ico">✓</span><div><h4>TOON_IA détectée</h4><p>Le déploiement peut appeler Gemini. Chaque génération utilise votre quota.</p></div></div>'
            : '<div class="alert alert-danger" style="margin-bottom:12px"><span class="alert-ico">⛔</span><div><h4>TOON_IA introuvable</h4><p>Ajoutez la variable <span class="mono">TOON_IA</span> dans Vercel → Settings → Environment Variables (Production), puis redéployez. Sans elle, aucune génération n’est possible.</p></div></div>') +
          '<label class="field"><span>Modèle</span><select class="select" data-bind="settings.model" data-kind="select">' +
            T.ai.MODELS.map((m) => '<option' + (s.model === m ? ' selected' : '') + '>' + m + '</option>').join('') + '</select>' +
            '<span class="tiny" style="display:block;margin-top:6px">Les modèles réellement disponibles dépendent de la clé et de la région. <span class="mono">GEMINI_MODEL</span> côté Vercel force le modèle par défaut.</span></label>' +
          '<div class="row"><button class="btn btn-primary" data-action="test-key">Tester la connexion</button>' +
          '<button class="btn btn-ghost" data-action="go" data-route="accueil">Retour au brief</button></div>' +
        '</div>' +
        '<div class="card" style="margin-top:16px"><h3>Stockage & projet</h3>' +
          '<p class="small">L’état est conservé dans le <span class="mono">localStorage</span> de ce navigateur. Aucune clé n’y est enregistrée.</p>' +
          '<div class="row"><button class="btn btn-ghost" data-action="export-all">Exporter tous les projets</button>' +
          '<button class="btn btn-danger" data-action="reset-all">Réinitialiser l’application</button></div>' +
          '<div class="divider"></div><div class="row"><button class="btn btn-ghost btn-sm" data-action="delete-project">Supprimer le projet actif</button></div>' +
        '</div></div>' +
        '<div class="card"><h3>État du moteur</h3>' +
          '<dl class="kv"><dt>Clé serveur</dt><dd>' + (ok ? 'configurée (TOON_IA)' : 'absente') + '</dd>' +
          '<dt>Modèle</dt><dd>' + esc(s.model) + '</dd>' +
          '<dt>Proxy</dt><dd class="mono">POST /api/gemini</dd>' +
          '<dt>Clé dans le navigateur</dt><dd>aucune</dd></dl>' +
          '<div class="alert alert-warn" style="margin-top:12px"><span class="alert-ico">⚡</span><div><h4>Quotas</h4><p>Chaque génération consomme du quota Gemini. La génération complète enchaîne une vingtaine d’appels : lancez-la sur un brief déjà réfléchi.</p></div></div>' +
          '<div class="alert alert-info" style="margin-top:10px"><span class="alert-ico">◈</span><div><h4>Visuels</h4><p>Les panels et la couverture restent des compositions SVG procédurales : aucun modèle d’image n’est appelé. Les prompts visuels sont fournis prêts à l’emploi.</p></div></div>' +
          '<div class="divider"></div><p class="small" style="margin:0">Projets enregistrés : <b>' + T.state.projects.length + '</b></p>' +
        '</div></div>';
  };
})();
