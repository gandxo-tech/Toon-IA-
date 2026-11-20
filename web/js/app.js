/* =========================================================
   ToonIA Studio — app.js
   Routage, actions, validation canonique, export, réglages.
   ========================================================= */
(function () {
  'use strict';

  const T = window.T;
  const Views = window.Views;
  const esc = T.esc;

  T.ui = { sceneId: null, canonTab: 'overview', contradictions: [] };
  let pendingConfirm = null;

  const ROUTES = {
    accueil: { title: 'Accueil', sub: 'Studio de webtoon assisté par IA', fn: Views.accueil, group: 'Atelier' },
    projet: { title: 'Idée & genèse', sub: 'Étape 1 — analyse de l’idée', fn: Views.projet },
    personnages: { title: 'Personnages', sub: 'Étape 2 — Character Bible', fn: Views.personnages },
    univers: { title: 'Univers & lieux', sub: 'Étape 3 — World Bible', fn: Views.univers },
    histoire: { title: 'Histoire & arc', sub: 'Étape 4 — Story Bible', fn: Views.histoire },
    direction: { title: 'Direction art & humour', sub: 'Étape 5 — Visual Bible & Humor DNA', fn: Views.direction },
    chapitre: { title: 'Chapitres & scènes', sub: 'Étape 6 — scripts structurés', fn: Views.chapitre },
    storyboard: { title: 'Storyboard', sub: 'Étape 7 — mise en scène', fn: Views.storyboard },
    panels: { title: 'Panels', sub: 'Étape 8 — découpage', fn: Views.panels },
    dialogues: { title: 'Dialogues', sub: 'Étape 9 — bulles', fn: Views.dialogues },
    lettering: { title: 'Lettering & bulles', sub: 'Étape 10 — rendu natif', fn: Views.lettering },
    couverture: { title: 'Couverture', sub: 'Étape 11 — packaging éditorial', fn: Views.couverture },
    canon: { title: 'Canon Memory', sub: 'Faits validés & contradictions', fn: Views.canon },
    lecture: { title: 'Lecture verticale', sub: 'Format webtoon', fn: Views.lecture },
    export: { title: 'Export', sub: 'JSON, Markdown, PDF', fn: Views.export },
    reglages: { title: 'Moteur & réglages', sub: 'Clé serveur, modèle & stockage', fn: Views.reglages }
  };

  const DOTS = {
    projet: (p) => !!p.analysis,
    personnages: (p) => !!(p.characters || []).length,
    univers: (p) => !!p.world,
    histoire: (p) => !!p.story,
    direction: (p) => !!(p.visual && p.humor),
    chapitre: (p) => !!(p.chapters || []).length,
    storyboard: (p) => (p.chapters || []).some((c) => (c.scenes || []).some((s) => !!s.detail)),
    panels: (p) => (p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).length)),
    dialogues: (p) => (p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).some((pn) => (pn.dialogues || []).length))),
    couverture: (p) => !!p.marketing,
    canon: (p) => {
      const c = T.canon.counts(p);
      return c.characters + c.locations + c.chapters + c.memories > 0;
    },
    lecture: (p) => (p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).length))
  };

  /* ---------------- routage ---------------- */
  function currentRoute() {
    const h = (location.hash || '').replace(/^#\/?/, '').split('?')[0];
    return ROUTES[h] ? h : 'accueil';
  }

  function navigate(route) {
    if (location.hash === '#/' + route) render();
    else location.hash = '#/' + route;
  }

  function setSidebar(open) {
    const sb = document.getElementById('sidebar');
    if (sb) sb.classList.toggle('is-open', open);
    document.body.classList.toggle('sidebar-open', open);
  }

  /* Dock de génération : reste visible quelle que soit la page ouverte. */
  function renderJobDock() {
    const dock = document.getElementById('job-dock');
    if (!dock) return;
    if (!T.job) { dock.innerHTML = ''; return; }
    dock.innerHTML = Views.jobPanel(T.job, { dock: true });
  }

  let jobTicker = null;
  function startJobTicker() {
    stopJobTicker();
    jobTicker = setInterval(() => { if (T.job) renderJobDock(); }, 1000);
  }
  function stopJobTicker() {
    if (jobTicker) { clearInterval(jobTicker); jobTicker = null; }
  }

  /* ---------------- rendu ---------------- */
  let restoringScroll = false;

  function render(opts) {
    const p = T.project();
    if (!p) return;
    const route = currentRoute();
    const def = ROUTES[route] || ROUTES.accueil;
    const view = document.getElementById('view');
    const y = window.scrollY;

    let html;
    try {
      html = def.fn(p);
    } catch (e) {
      console.error(e);
      html = '<div class="alert alert-danger"><span class="alert-ico">⛔</span><div><h4>Erreur de rendu</h4><p>' + esc(e.message) + '</p></div></div>';
    }

    view.innerHTML = '<div class="print-page">' + html + '</div>';
    document.getElementById('topbar-title').textContent = def.title;
    document.getElementById('topbar-sub').textContent = def.sub || '';

    document.querySelectorAll('.nav-item[data-route]').forEach((el) => {
      el.classList.toggle('is-active', el.dataset.route === route);
    });

    Object.keys(DOTS).forEach((key) => {
      const dot = document.querySelector('[data-dot="' + key + '"]');
      if (!dot) return;
      let done = false;
      try { done = DOTS[key](p); } catch (_) {}
      dot.classList.toggle('is-empty', !done);
    });

    const sel = document.getElementById('project-select');
    sel.innerHTML = T.state.projects.map((pr) =>
      '<option value="' + esc(pr.id) + '"' + (pr.id === p.id ? ' selected' : '') + '>' + esc(T.clamp(pr.title, 34)) + '</option>').join('');

    const badge = document.getElementById('engine-badge');
    badge.textContent = T.ai.engineLabel();
    badge.className = 'badge ' + (T.ai.hasServerKey() ? 'badge-canon' : 'badge-ai');

    setSidebar(false);
    renderJobDock();

    if (opts && opts.keepScroll) {
      restoringScroll = true;
      requestAnimationFrame(() => { window.scrollTo(0, y); restoringScroll = false; });
    } else {
      window.scrollTo(0, 0);
    }
  }

  /* ---------------- génération ---------------- */
  async function run(key, fn) {
    T.busy = key;
    T.lastError = null;
    if (T.job && T.job.state === 'error') T.job = null;
    render({ keepScroll: true });
    startJobTicker();
    try {
      await fn();
      T.retry = null;
      T.job = null;
    } catch (e) {
      T.lastError = e && e.message ? e.message : String(e);
      T.retry = fn;
      // L'échec reste affiché (accueil + dock) au lieu de disparaître avec le toast.
      if (T.job) {
        T.job.state = 'error';
        T.job.error = T.lastError;
        T.job.endedAt = Date.now();
      }
      // Le panneau d'échec (accueil + dock) porte déjà le message : pas de toast en double.
      if (!T.job) T.toast('Échec de la génération', 'error');
    } finally {
      stopJobTicker();
      T.busy = null;
      T.save();
      render({ keepScroll: true });
    }
  }

  /* ---------------- modale ---------------- */
  function openModal(html) {
    document.getElementById('modal-root').innerHTML =
      '<div class="modal-backdrop" data-action="close-modal"><div class="modal" onclick="event.stopPropagation()">' + html + '</div></div>';
  }
  function closeModal() {
    document.getElementById('modal-root').innerHTML = '';
    pendingConfirm = null;
  }

  function contradictionBlock(list) {
    return list.map((x) =>
      '<div class="alert ' + (x.severity === 'HIGH' ? 'alert-danger' : 'alert-warn') + '" style="margin-top:8px">' +
      '<span class="alert-ico">' + (x.severity === 'HIGH' ? '⛔' : '⚠') + '</span><div>' +
      '<h4>' + esc(x.targetType) + ' · ' + esc(x.fieldName) + '</h4>' +
      '<p>' + esc(x.description) + '</p>' +
      '<p class="tiny" style="margin-top:5px">Canon : « ' + esc(T.clamp(x.canonicalValue, 120)) + ' »<br>Nouveau : « ' + esc(T.clamp(x.conflictingValue, 120)) + ' »</p></div></div>').join('');
  }

  function confirmWithContradictions(title, list, onConfirm, confirmLabel) {
    if (!list.length) { onConfirm(); return; }
    pendingConfirm = onConfirm;
    openModal('<div class="eyebrow">Canon Memory</div><h2 style="margin-top:0">' + esc(title) + '</h2>' +
      '<p class="small">' + list.length + ' contradiction(s) détectée(s). Le moteur ne corrige jamais automatiquement : c’est à vous de décider si le canon évolue.</p>' +
      contradictionBlock(list) +
      '<div class="row" style="margin-top:18px"><button class="btn btn-primary" data-action="confirm-pending">' + esc(confirmLabel || 'Faire évoluer le canon') + '</button>' +
      '<button class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
  }

  /* ---------------- validation canonique ---------------- */
  function addMemory(p, category, fact, impact) {
    p.canon.memories = p.canon.memories || [];
    if (p.canon.memories.some((m) => m.fact === fact)) return;
    p.canon.memories.push({ id: T.uid('mem'), category, fact, impact: impact || '', createdAt: T.now() });
  }

  function validateCharacter(id) {
    const p = T.project();
    const c = (p.characters || []).find((x) => x.id === id);
    if (!c) return;
    const list = T.canon.detectCharacter(c, p);
    confirmWithContradictions('Valider « ' + c.name + ' » dans le canon', list, () => {
      p.canon.characters = p.canon.characters || [];
      if (!p.canon.characters.some((x) => x.name.toLowerCase() === c.name.toLowerCase())) {
        p.canon.characters.push(JSON.parse(JSON.stringify(Object.assign({}, c, { isValidated: true }))));
      }
      addMemory(p, 'Personnage', c.name + ' — ' + c.role + '. ' + T.clamp(c.physicalAppearance, 160), 'Contrainte de continuité visuelle et narrative.');
      T.save(); closeModal(); T.toast(c.name + ' ajouté au canon', 'ok'); render();
    }, 'Ajouter au canon malgré tout');
  }

  function validateLocation(id) {
    const p = T.project();
    const l = ((p.world && p.world.locations) || []).find((x) => x.id === id);
    if (!l) return;
    const list = T.canon.detectLocation(l, p);
    confirmWithContradictions('Valider le lieu « ' + l.name + ' »', list, () => {
      p.canon.locations = p.canon.locations || [];
      if (!p.canon.locations.some((x) => x.id === l.id)) p.canon.locations.push(JSON.parse(JSON.stringify(l)));
      addMemory(p, 'Lieu', l.name + ' — ' + T.clamp(l.description, 160) + ' Atmosphère : ' + l.atmosphere, 'Décor canonique réutilisable.');
      T.save(); closeModal(); T.toast('Lieu ajouté au canon', 'ok'); render();
    }, 'Ajouter malgré tout');
  }

  function validateWorld() {
    const p = T.project();
    const w = p.world;
    if (!w) return;
    (w.locations || []).forEach((l) => {
      p.canon.locations = p.canon.locations || [];
      if (!p.canon.locations.some((x) => x.id === l.id)) p.canon.locations.push(JSON.parse(JSON.stringify(l)));
    });
    p.canon.worldBible = { rules: w.rules, factions: w.factions, geography: w.geography, socialStructure: w.socialStructure, historyLore: w.historyLore, terminology: w.terminology, worldConstraints: w.worldConstraints };
    addMemory(p, 'Règle Univers', T.clamp(w.rules, 260), 'Loi fondamentale : toute génération doit la respecter.');
    addMemory(p, 'Contrainte Univers', T.clamp(w.worldConstraints, 220), 'Interdits créatifs du monde.');
    w.isValidated = true;
    T.save(); T.toast('Univers validé dans le canon', 'ok'); render();
  }

  function validateStory() {
    const p = T.project();
    const s = p.story;
    if (!s) return;
    const list = [];
    const prev = p.canon.storyBible;
    if (prev && prev.mainThemes && s.mainThemes && prev.mainThemes.toLowerCase() !== s.mainThemes.toLowerCase()) {
      list.push({
        targetType: 'Bible Histoire', targetName: 'Thème', fieldName: 'Thème principal',
        canonicalValue: prev.mainThemes, conflictingValue: s.mainThemes,
        description: 'Le thème principal canonique est modifié. Une évolution thématique doit être assumée explicitement.',
        severity: 'MEDIUM'
      });
    }
    confirmWithContradictions('Valider la Story Bible', list, () => {
      p.canon.storyBible = { mainThemes: s.mainThemes, narrativeDirection: s.narrativeDirection, generalTone: s.generalTone, premise: s.premise };
      addMemory(p, 'Thème', T.clamp(s.mainThemes, 200), 'Orientation thématique du récit.');
      addMemory(p, 'Direction', T.clamp(s.narrativeDirection, 260), 'Promesse finale et climax visés.');
      (s.timelineEvents || []).forEach((e) => addMemory(p, 'Événement clé', e.dateOrEpoch + ' — ' + e.title + ' : ' + T.clamp(e.description, 160), 'Fait antérieur au chapitre 1.'));
      s.isValidated = true;
      T.save(); closeModal(); T.toast('Histoire validée dans le canon', 'ok'); render();
    }, 'Mettre à jour le canon');
  }

  function validateVisual() {
    const p = T.project();
    if (!p.visual) return;
    const list = [];
    const prev = p.canon.visualBible;
    if (prev && prev.artStyle && p.visual.artStyle && prev.artStyle.toLowerCase() !== p.visual.artStyle.toLowerCase()) {
      list.push({
        targetType: 'Direction artistique', targetName: 'Style graphique', fieldName: 'Direction artistique générale',
        canonicalValue: prev.artStyle, conflictingValue: p.visual.artStyle,
        description: 'La direction artistique canonique est modifiée. Tout le projet devra être régénéré avec le nouveau style.',
        severity: 'MEDIUM'
      });
    }
    confirmWithContradictions('Valider la direction artistique', list, () => {
      p.canon.visualBible = JSON.parse(JSON.stringify(p.visual));
      addMemory(p, 'Direction artistique', p.visual.artStyle + ' · ' + p.visual.colorPalette, 'Contrainte de génération visuelle.');
      p.visual.isValidated = true;
      T.save(); closeModal(); T.toast('Direction artistique validée', 'ok'); render();
    }, 'Mettre à jour le canon');
  }

  function validateHumor() {
    const p = T.project();
    if (!p.humor) return;
    const list = [];
    const sb = p.canon.storyBible;
    if (sb) {
      const grim = /sombre|tragique|mort|tragédie/i.test(sb.mainThemes || '') || /sombre|tragique/i.test(sb.narrativeDirection || '');
      const burlesque = /burlesque|slapstick|bouffon/i.test(p.humor.humorStyle || '');
      if (grim && burlesque) {
        list.push({
          targetType: 'Humor DNA', targetName: 'Harmonie tonale', fieldName: 'Dissonance humour / ton',
          canonicalValue: 'Récit sombre ou tragique (' + sb.mainThemes + ')', conflictingValue: p.humor.humorStyle,
          description: 'Le registre comique risque d’affaiblir la tension dramatique majeure établie dans la Story Bible.',
          severity: 'MEDIUM'
        });
      }
    }
    confirmWithContradictions('Valider l’Humor DNA', list, () => {
      p.canon.humorDNA = JSON.parse(JSON.stringify(p.humor));
      addMemory(p, 'Humour', p.humor.humorStyle + '. Running gags : ' + T.clamp(p.humor.runningGags, 140), 'Registre comique canonique.');
      p.humor.isValidated = true;
      T.save(); closeModal(); T.toast('Humor DNA validé', 'ok'); render();
    }, 'Mettre à jour le canon');
  }

  function validateAnalysis() {
    const p = T.project();
    if (!p.analysis) return;
    p.validated = p.validated || {};
    p.validated.analysis = true;
    addMemory(p, 'Genre', p.analysis.genrePrincipal + ' — ' + (p.analysis.sousGenres || []).join(', '), 'Cadre générique du projet.');
    addMemory(p, 'Ton', p.analysis.ton, 'Registre narratif principal.');
    if (p.analysis.themesDetectes && p.analysis.themesDetectes.length) addMemory(p, 'Thèmes', p.analysis.themesDetectes.join(', '), 'Thèmes directeurs.');
    if (p.idea) addMemory(p, 'Prémisse', T.clamp(p.idea, 280), 'Idée fondatrice, à ne pas contredire.');
    T.save(); T.toast('Idée validée — passage aux personnages', 'ok');
    navigate('personnages');
  }

  function validateChapter(number) {
    const p = T.project();
    const ch = [...(p.chapters || [])].reverse().find((c) => Number(c.number) === Number(number));
    if (!ch) return;
    const list = T.canon.detectChapter(ch, p);
    confirmWithContradictions('Valider le chapitre ' + ch.number, list, () => {
      p.canon.chapters = p.canon.chapters || [];
      if (!p.canon.chapters.some((x) => x.id === ch.id)) p.canon.chapters.push(JSON.parse(JSON.stringify({ id: ch.id, number: ch.number, title: ch.title, synopsis: ch.synopsis })));
      addMemory(p, 'Événement clé', 'Chapitre ' + ch.number + ' — ' + ch.title + ' : ' + T.clamp(ch.synopsis, 200), 'Contenu canonique validé.');
      ch.isCanon = true;
      T.save(); closeModal(); T.toast('Chapitre validé au canon', 'ok'); render();
    }, 'Valider quand même');
  }

  function validateCover() {
    const p = T.project();
    const m = p.marketing;
    if (!m) return;
    const cover = {
      title: m.selectedTitle || p.title,
      tagline: m.selectedTagline || '',
      visualDescription: (m.coverProposal && m.coverProposal.detailedDescriptionNoText) || m.selectedCoverDescription || ''
    };
    const list = T.canon.detectCover(cover, p).concat(T.canon.detectSynopsis(cover.title, m.selectedSynopsis || '', p));
    confirmWithContradictions('Valider la couverture et le synopsis', list, () => {
      p.canon.cover = cover;
      p.canon.project = { finalTitle: cover.title, finalSynopsis: m.selectedSynopsis || '' };
      addMemory(p, 'Titre', cover.title, 'Titre éditorial validé.');
      addMemory(p, 'Synopsis', T.clamp(m.selectedSynopsis || '', 300), 'Synopsis officiel du projet.');
      m.isValidated = true;
      T.save(); closeModal(); T.toast('Couverture validée au canon', 'ok'); render();
    }, 'Valider malgré tout');
  }

  /* ---------------- dialogues ---------------- */
  function findPanel(p, panelId) {
    for (const ch of p.chapters || []) for (const s of ch.scenes || []) for (const pn of s.panels || []) if (pn.id === panelId) return pn;
    return null;
  }
  function findScene(p, sceneId) {
    for (const ch of p.chapters || []) for (const s of ch.scenes || []) if (s.id === sceneId) return s;
    return null;
  }

  /* ---------------- export ---------------- */
  function download(filename, content, type) {
    const blob = new Blob([content], { type: type || 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  function projectMarkdown(p) {
    const m = p.marketing || {};
    const lines = [];
    lines.push('# ' + (m.selectedTitle || p.title));
    if (m.selectedTagline) lines.push('', '> ' + m.selectedTagline);
    lines.push('', '## Synopsis', '', m.selectedSynopsis || p.idea || '—');
    if (p.analysis) {
      lines.push('', '**Genre** : ' + p.analysis.genrePrincipal + ' · **Ton** : ' + p.analysis.ton,
        '**Thèmes** : ' + (p.analysis.themesDetectes || []).join(', '));
    }
    lines.push('', '## Personnages canoniques');
    if ((p.canon.characters || []).length) {
      p.canon.characters.forEach((c) => {
        lines.push('', '### ' + c.name + ' — ' + c.role, '', '- Archétype : ' + c.archetype, '- Personnalité : ' + c.personality,
          '- Apparence : ' + c.physicalAppearance, '- Signes distinctifs : ' + c.distinctiveFeatures,
          '- Tenue : ' + c.characteristicClothing, '- Capacités : ' + c.abilities,
          '- Relations : ' + c.relationships, '- Arc : ' + c.plannedArc, '- État initial : ' + c.currentState);
      });
    } else lines.push('', '_Aucun personnage validé._');

    if (p.canon.worldBible) {
      lines.push('', '## Univers');
      const w = p.canon.worldBible;
      lines.push('', '**Règles** : ' + w.rules, '', '**Factions** : ' + w.factions, '', '**Géographie** : ' + w.geography,
        '', '**Structure sociale** : ' + w.socialStructure, '', '**Lore** : ' + w.historyLore,
        '', '**Terminologie** : ', '', w.terminology, '', '**Contraintes** : ' + w.worldConstraints);
    }
    if ((p.canon.locations || []).length) {
      lines.push('', '### Lieux');
      p.canon.locations.forEach((l) => lines.push('', '**' + l.name + '** — ' + l.description, '_' + l.atmosphere + '_'));
    }
    if (p.canon.storyBible) {
      lines.push('', '## Story Bible');
      const s = p.canon.storyBible;
      lines.push('', '**Prémisse** : ' + s.premise, '', '**Thèmes** : ' + s.mainThemes, '', '**Ton** : ' + s.generalTone,
        '', '**Direction** : ' + s.narrativeDirection);
    }
    lines.push('', '## Chapitres');
    if ((p.chapters || []).length) {
      p.chapters.forEach((ch) => {
        lines.push('', '### Chapitre ' + ch.number + ' — ' + ch.title, '', ch.synopsis);
        (ch.scenes || []).forEach((s) => {
          lines.push('', '#### Scène ' + s.orderIndex + ' — ' + s.title, '', '- **Lieu** : ' + s.locationName,
            '- **Personnages** : ' + (s.charactersPresent || []).join(', '), '- **Résumé** : ' + s.summary);
          if ((s.beats || []).length) { lines.push('- **Beats** :'); s.beats.forEach((b) => lines.push('  - ' + b)); }
          (s.panels || []).forEach((pn) => {
            lines.push('', '**Panel #' + pn.orderIndex + '** — ' + pn.cameraAngle + ' · ' + pn.dominantEmotion,
              '', '_' + pn.actionDescription + '_', '',
              '- Composition : ' + pn.composition, '- Lumière : ' + pn.lightingAndAtmosphere,
              '- Décor : ' + pn.backgroundDetails, '- Continuité : ' + pn.continuityNotes);
            if (pn.soundEffects) lines.push('- Onomatopée : ' + pn.soundEffects);
            (pn.dialogues || []).forEach((d) => lines.push('', '> **' + d.speakerName + '** (' + d.type + ', ' + d.placement + ') — ' + d.text));
          });
        });
      });
    } else lines.push('', '_Aucun chapitre._');
    lines.push('', '---', '', 'Généré par ToonIA Studio — portage web HTML/CSS/JS.', '');
    return lines.join('\n');
  }

  function exportAll() {
    download('toonia-projets-' + new Date().toISOString().slice(0, 10) + '.json', JSON.stringify({ exportedAt: new Date().toISOString(), projects: T.state.projects }, null, 2), 'application/json');
    T.toast('Archive complète téléchargée', 'ok');
  }

  /* ---------------- presets ---------------- */
  const PRESET_VAGUE = "C'est l'histoire d'un jeune héros qui a des super pouvoirs et qui se bat contre une organisation de méchants dans une grande ville.";
  const PRESET_DETAILED = "Dans un Paris cyberpunk de 2088 submergé par les pluies acides, Kael, un mécanicien clandestin de membres bioniques, découvre un implant quantique prohibé qui appartenait à sa sœur disparue. Cet implant lui permet de rembobiner le temps de 5 secondes, mais à chaque utilisation, son propre système nerveux se calcifie. Pourchassé par les agents de la corporation Chimera Dynamics, il doit s'allier avec une hackeuse rebelle pour démanteler le réseau avant que son corps ne cède.";

  const ADV_DETAILED = {
    genre: 'Cyberpunk / Sci-Fi',
    ambiance: 'Sombre, pluvieux, haute technologie et bas-fonds',
    epoque: 'Futur dystopique (2088)',
    personnagesSouhaites: 'Kael (protagoniste résilient), Mira (hackeuse sarcastique)',
    inspirations: 'Akira, Blade Runner, Arcane',
    longueurVisee: 'Série longue (~60 chapitres)',
    publicCible: 'Jeunes adultes (16-30 ans)',
    contraintes: 'Règle du retour temporel limitée à 5 secondes avec contrepartie physique directe',
    evenementsSouhaites: ''
  };

  /* ---------------- génération complète depuis le brief ---------------- */
  function seedCanon(p) {
    if ((p.canon.characters || []).length) return;
    p.canon.characters = (p.characters || []).map((c) => JSON.parse(JSON.stringify(Object.assign({}, c, { isValidated: true }))));
    p.canon.locations = ((p.world && p.world.locations) || []).map((l) => JSON.parse(JSON.stringify(l)));
    (p.characters || []).forEach((c) => addMemory(p, 'Personnage', c.name + ' — ' + c.role + '. ' + T.clamp(c.physicalAppearance, 160), 'Continuité visuelle et narrative.'));
    if (p.world) {
      p.canon.worldBible = {
        rules: p.world.rules, factions: p.world.factions, geography: p.world.geography,
        socialStructure: p.world.socialStructure, historyLore: p.world.historyLore,
        terminology: p.world.terminology, worldConstraints: p.world.worldConstraints
      };
      addMemory(p, 'Règle Univers', T.clamp(p.world.rules, 260), 'Loi fondamentale du monde.');
      addMemory(p, 'Contrainte Univers', T.clamp(p.world.worldConstraints, 220), 'Interdits créatifs.');
      (p.world.locations || []).forEach((l) => addMemory(p, 'Lieu', l.name + ' — ' + T.clamp(l.description, 160) + ' Atmosphère : ' + l.atmosphere, 'Décor canonique.'));
    }
    if (p.story) {
      p.canon.storyBible = {
        mainThemes: p.story.mainThemes, narrativeDirection: p.story.narrativeDirection,
        generalTone: p.story.generalTone, premise: p.story.premise
      };
      addMemory(p, 'Thème', T.clamp(p.story.mainThemes, 200), 'Orientation thématique du récit.');
      (p.story.timelineEvents || []).forEach((e) => addMemory(p, 'Événement clé', e.dateOrEpoch + ' — ' + e.title + ' : ' + T.clamp(e.description, 160), 'Fait antérieur au chapitre 1.'));
    }
    if (p.marketing) {
      p.canon.cover = {
        title: p.marketing.selectedTitle || p.title,
        tagline: p.marketing.selectedTagline || '',
        visualDescription: (p.marketing.coverProposal && p.marketing.coverProposal.detailedDescriptionNoText) || ''
      };
      p.canon.project = { finalTitle: p.canon.cover.title, finalSynopsis: p.marketing.selectedSynopsis || '' };
      addMemory(p, 'Titre', p.canon.cover.title, 'Titre éditorial validé.');
    }
  }

  async function generateProject() {
    const p = T.project();
    if (!p) throw new Error('Aucun projet actif.');
    if (String(p.idea || '').trim().length < 40) {
      throw new Error('Le brief est trop court : décrivez l’univers, les personnages, le ton et l’arc en quelques phrases.');
    }
    if (T.ai.server.checked && !T.ai.hasServerKey()) {
      throw new Error('TOON_IA est absente de ce déploiement. Ajoutez la variable dans Vercel → Settings → Environment Variables, puis redéployez.');
    }

    const chapters = Math.min(3, Math.max(1, Number(T.state.settings.chapters) || 1));
    const softErrors = [];
    const phases = [
      ['Analyse du brief', 'Genre, ton, thèmes et ambiguïtés'],
      ['Character Bible', 'Distribution, apparences et arcs'],
      ['World Bible', 'Règles, lieux et factions'],
      ['Story Bible', 'Structure, actes et timeline'],
      ['Direction artistique', 'Palette, cadrage et Humor DNA'],
      ['Chapitres', chapters + ' script' + (chapters > 1 ? 's' : '') + ' à écrire'],
      ['Panels & bulles', 'Découpage du chapitre 1'],
      ['Couverture', 'Titres, synopsis et visuel'],
      ['Canon', 'Mémorisation des faits validés']
    ];
    const job = (T.job = {
      startedAt: Date.now(),
      label: phases[0][0],
      detail: phases[0][1],
      steps: phases.map((ph) => ({ label: ph[0], state: 'pending' })),
      done: 0,
      total: phases.length
    });
    const renderJob = () => {
      renderJobDock();
      const host = document.getElementById('job-inline-host');
      if (host) host.innerHTML = Views.jobPanel(T.job);
    };
    const phase = (i, detail) => {
      job.steps.forEach((s, idx) => { s.state = idx < i ? 'done' : idx === i ? 'active' : 'pending'; });
      job.done = i;
      job.label = phases[i] ? phases[i][0] : job.label;
      job.detail = detail || (phases[i] ? phases[i][1] : '');
      renderJob();
    };
    const checkpoint = () => { p.updatedAt = T.now(); T.save(); };
    const finish = () => {
      job.steps.forEach((s) => { s.state = 'done'; });
      job.done = phases.length;
      job.endedAt = Date.now();
      renderJob();
    };

    phase(0);
    p.analysis = await T.ai.analyze(p);
    checkpoint();

    phase(1);
    p.characters = await T.ai.characters(p);
    checkpoint();

    phase(2);
    p.world = await T.ai.world(p);
    checkpoint();

    phase(3);
    p.story = await T.ai.story(p);
    checkpoint();

    phase(4);
    const vh = await T.ai.visualHumor(p);
    p.visual = vh.visual;
    p.humor = vh.humor;
    checkpoint();

    p.chapters = [];
    let first = null;
    for (let n = 1; n <= chapters; n++) {
      phase(5, 'Chapitre ' + n + ' — écriture du script');
      const ch = await T.ai.chapter(p, n);
      p.chapters.push(ch);
      if (n === 1) first = ch;
      checkpoint();
    }

    if (first) {
      const scenes = first.scenes || [];
      let sceneIdx = 0;
      for (const scene of scenes) {
        sceneIdx += 1;
        phase(6, 'Scène ' + sceneIdx + '/' + scenes.length + ' — ' + scene.title + ' : cadrage des panels');
        try {
          scene.panels = await T.ai.panels(p, scene);
        } catch (e) {
          scene.panels = [];
          softErrors.push('Panels « ' + scene.title + ' » : ' + (e.message || 'échec'));
        }
        let panelIdx = 0;
        for (const panel of scene.panels || []) {
          panelIdx += 1;
          phase(6, 'Scène ' + sceneIdx + '/' + scenes.length + ' — bulles ' + panelIdx + '/' + scene.panels.length);
          try {
            panel.dialogues = await T.ai.dialogues(p, panel);
          } catch (e) {
            panel.dialogues = [];
            softErrors.push('Bulles panel ' + panel.orderIndex + ' : ' + (e.message || 'échec'));
          }
        }
        checkpoint();
      }
      if (scenes[0]) T.ui.sceneId = scenes[0].id;
    }

    phase(7);
    p.marketing = await T.ai.marketing(p);
    checkpoint();

    phase(8);
    seedCanon(p);
    checkpoint();
    finish();
    T.toast('Projet complet généré · ' + chapters + ' chapitre(s), ' + T.stats(p).panels + ' panels', 'ok');
    if (softErrors.length) T.toast(softErrors.length + ' étape(s) partielle(s) : utilisez « Réessayer » dans les modules concernés', 'error');
    navigate('couverture');
  }

  function applyBriefPreset(id) {
    const preset = (T.briefPresets || []).find((x) => x.id === id);
    if (!preset) return;
    T.update((pr) => {
      pr.idea = preset.idea;
      if (!pr.title || pr.title === 'Nouveau webtoon' || pr.title === 'Mon webtoon') pr.title = preset.title;
    });
    render();
    T.toast('Amorce « ' + preset.label + ' » chargée — modifiez-la librement', 'ok');
  }

  /* ---------------- actions ---------------- */
  const actions = {
    go: (el) => navigate(el.dataset.route),
    toggleSidebar: () => setSidebar(!document.getElementById('sidebar').classList.contains('is-open')),
    closeSidebar: () => setSidebar(false),
    'skip-content': () => {
      const v = document.getElementById('view');
      if (!v) return;
      v.setAttribute('tabindex', '-1');
      v.focus();
      v.scrollIntoView({ block: 'start' });
    },
    'toggle-job': () => { T.ui.jobFolded = !T.ui.jobFolded; renderJobDock(); },
    'brief-preset': (el) => {
      const preset = (T.briefPresets || []).find((x) => x.id === el.dataset.preset);
      if (!preset) return;
      const p = T.project();
      if (!String(p.idea || '').trim()) { applyBriefPreset(preset.id); return; }
      openModal('<div class="eyebrow">Amorce</div><h2 style="margin-top:0">Remplacer le brief actuel ?</h2>' +
        '<p class="small">L’amorce « ' + esc(preset.label) + ' » remplacera le texte présent. Le reste du projet n’est pas modifié.</p>' +
        '<div class="row" style="margin-top:16px"><button type="button" class="btn btn-primary" data-action="apply-brief-preset" data-preset="' + esc(preset.id) + '">Remplacer le brief</button><button type="button" class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
    },
    'apply-brief-preset': (el) => { closeModal(); applyBriefPreset(el.dataset.preset); },
    'close-modal': () => closeModal(),
    'confirm-pending': () => { const fn = pendingConfirm; closeModal(); if (fn) fn(); },
    'clear-error': () => { T.lastError = null; if (T.job && T.job.state === 'error') T.job = null; render(); },
    'dismiss-job': () => { T.job = null; render({ keepScroll: true }); },
    'retry-last': () => { if (T.retry) run('retry', T.retry); },

    'new-project': () => {
      openModal('<div class="eyebrow">Nouveau projet</div><h2 style="margin-top:0">Créer un webtoon</h2>' +
        '<label class="field"><span>Titre de travail</span><input class="input" id="np-title" placeholder="Ex : Les Fissures du Dôme" /></label>' +
        '<label class="field"><span>Idée de départ (facultatif)</span><textarea class="textarea" id="np-idea" placeholder="Décrivez une prémisse, même en une phrase."></textarea></label>' +
        '<div class="row" style="margin-top:16px"><button class="btn btn-primary" data-action="create-project">Créer le projet</button><button class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
      setTimeout(() => { const i = document.getElementById('np-title'); if (i) i.focus(); }, 40);
    },
    'create-project': () => {
      const title = (document.getElementById('np-title') || {}).value || 'Nouveau webtoon';
      const idea = (document.getElementById('np-idea') || {}).value || '';
      const proj = T.blankProject(title.trim() || 'Nouveau webtoon', idea.trim());
      T.state.projects.push(proj);
      T.state.activeId = proj.id;
      T.save(); closeModal();
      T.ui = { sceneId: null, canonTab: 'overview', contradictions: [] };
      navigate('projet');
      T.toast('Projet créé', 'ok');
    },
    'delete-project': () => {
      const p = T.project();
      openModal('<h2 style="margin-top:0">Supprimer « ' + esc(p.title) + ' » ?</h2><p class="small">Le projet et tout son canon seront retirés de ce navigateur. Action irréversible.</p>' +
        '<div class="row" style="margin-top:16px"><button class="btn btn-danger" data-action="really-delete-project">Supprimer définitivement</button><button class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
    },
    'really-delete-project': () => {
      const id = T.state.activeId;
      T.state.projects = T.state.projects.filter((p) => p.id !== id);
      if (!T.state.projects.length) T.state.projects.push(T.blankProject('Nouveau webtoon', ''));
      T.state.activeId = T.state.projects[0].id;
      T.save(); closeModal(); render(); T.toast('Projet supprimé');
    },
    'reset-all': () => {
      openModal('<h2 style="margin-top:0">Réinitialiser l’application ?</h2><p class="small">Tous les projets et les réglages enregistrés dans ce navigateur seront effacés.</p>' +
        '<div class="row" style="margin-top:16px"><button class="btn btn-danger" data-action="really-reset">Tout effacer</button><button class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
    },
    'really-reset': () => {
      localStorage.removeItem('toonia.web.v1');
      closeModal();
      location.hash = '#/accueil';
      T.load(); T.ui = { sceneId: null, canonTab: 'overview', contradictions: [] }; render();
      T.toast('Application réinitialisée');
    },

    'preset-vague': () => { T.update((p) => { p.idea = PRESET_VAGUE; p.advanced = {}; }); render({ keepScroll: true }); },
    'preset-detailed': () => { T.update((p) => { p.idea = PRESET_DETAILED; p.advanced = Object.assign({}, ADV_DETAILED); }); render({ keepScroll: true }); },

    analyze: () => run('analyse', async () => {
      const p = T.project();
      const res = await T.ai.analyze(p);
      p.analysis = res;
    }),

    'add-theme': () => { const v = prompt('Nouveau thème :'); if (v && v.trim()) { T.update((p) => { p.analysis.themesDetectes = (p.analysis.themesDetectes || []).concat(v.trim()); }); render({ keepScroll: true }); } },
    'remove-theme': (el) => { T.update((p) => { p.analysis.themesDetectes = (p.analysis.themesDetectes || []).filter((x) => x !== el.dataset.value); }); render({ keepScroll: true }); },
    'add-sub': () => { const v = prompt('Nouveau sous-genre :'); if (v && v.trim()) { T.update((p) => { p.analysis.sousGenres = (p.analysis.sousGenres || []).concat(v.trim()); }); render({ keepScroll: true }); } },
    'remove-sub': (el) => { T.update((p) => { p.analysis.sousGenres = (p.analysis.sousGenres || []).filter((x) => x !== el.dataset.value); }); render({ keepScroll: true }); },
    'validate-analysis': () => validateAnalysis(),

    'gen-characters': () => run('characters', async () => {
      const p = T.project();
      p.characters = await T.ai.characters(p);
    }),
    'validate-character': (el) => validateCharacter(el.dataset.id),

    'gen-world': () => run('world', async () => {
      const p = T.project();
      p.world = await T.ai.world(p);
    }),
    'validate-world': () => validateWorld(),
    'validate-location': (el) => validateLocation(el.dataset.id),

    'gen-story': () => run('story', async () => {
      const p = T.project();
      p.story = await T.ai.story(p);
    }),
    'validate-story': () => validateStory(),

    'gen-visual': () => run('visual', async () => {
      const p = T.project();
      const res = await T.ai.visualHumor(p);
      p.visual = res.visual;
      p.humor = res.humor;
    }),
    'validate-visual': () => validateVisual(),
    'validate-humor': () => validateHumor(),
    'direction-tab': (el) => { Views.local.directionTab = el.dataset.tab; render(); },
    'toggle-chibi': (el) => { T.update((p) => { p.humor.chibiMomentsAllowed = !!el.checked; }); render({ keepScroll: true }); },

    'gen-chapter': () => run('chapter', async () => {
      const p = T.project();
      const next = (p.chapters || []).reduce((m, c) => Math.max(m, Number(c.number) || 0), 0) + 1;
      const ch = await T.ai.chapter(p, next);
      p.chapters = p.chapters || [];
      p.chapters.push(ch);
      if (ch.scenes && ch.scenes[0]) T.ui.sceneId = ch.scenes[0].id;
    }),
    'validate-chapter': (el) => validateChapter(el.dataset.number),
    'open-scene': (el) => { T.ui.sceneId = el.dataset.scene; navigate('storyboard'); },
    'open-panels': (el) => { T.ui.sceneId = el.dataset.scene; navigate('panels'); },
    'open-lettering': () => navigate('lettering'),

    'gen-detail': () => run('detail', async () => {
      const p = T.project();
      const s = findScene(p, T.ui.sceneId);
      if (!s) throw new Error('Aucune scène sélectionnée.');
      s.detail = await T.ai.sceneDetail(p, s);
    }),
    'gen-all': () => run('all', generateProject),
    'gen-panels': () => run('panels', async () => {
      const p = T.project();
      const s = findScene(p, T.ui.sceneId);
      if (!s) throw new Error('Aucune scène sélectionnée.');
      const panels = await T.ai.panels(p, s);
      s.panels = panels.map((pn, i) => Object.assign(pn, { dialogues: pn.dialogues || [], orderIndex: i + 1, artSeed: pn.artSeed || (s.id + ':' + i) }));
    }),

    'gen-dialogues': (el) => run('dialogues', async () => {
      const p = T.project();
      const pn = findPanel(p, el.dataset.panel);
      if (!pn) throw new Error('Panel introuvable.');
      pn.dialogues = await T.ai.dialogues(p, pn);
    }),
    'add-dialogue': (el) => { T.update((p) => {
      const pn = findPanel(p, el.dataset.panel);
      if (!pn) return;
      pn.dialogues = pn.dialogues || [];
      pn.dialogues.push({ id: T.uid('dlg'), speakerName: (p.characters[0] || {}).name || 'Narrateur', type: 'SPEECH', emotion: 'Neutre', placement: 'TOP_LEFT', text: 'Nouvelle réplique…' });
    }); render({ keepScroll: true }); },
    'del-dialogue': (el) => { T.update((p) => {
      const pn = findPanel(p, el.dataset.panel);
      if (!pn) return;
      pn.dialogues = (pn.dialogues || []).filter((d) => d.id !== el.dataset.dialogue);
    }); render({ keepScroll: true }); },

    'gen-marketing': () => run('marketing', async () => {
      const p = T.project();
      p.marketing = await T.ai.marketing(p);
    }),
    'select-title': (el) => { T.update((p) => { p.marketing.selectedTitle = el.dataset.value; }); render({ keepScroll: true }); },
    'select-synopsis': (el) => { T.update((p) => { p.marketing.selectedSynopsis = el.dataset.value; }); render({ keepScroll: true }); },
    'validate-cover': () => validateCover(),

    'canon-tab': (el) => { T.ui.canonTab = el.dataset.tab; render(); T.ui.contradictions = T.ui.contradictions || []; },
    'add-memory': () => {
      openModal('<h2 style="margin-top:0">Ajouter un fait canonique</h2>' +
        '<label class="field"><span>Catégorie</span><input class="input" id="mem-cat" placeholder="Règle Univers, Mort, Révélation, Serment…" /></label>' +
        '<label class="field"><span>Fait établi</span><textarea class="textarea" id="mem-fact" placeholder="Ex : Aiden meurt au chapitre 3 et ne peut revenir."></textarea></label>' +
        '<div class="row" style="margin-top:14px"><button class="btn btn-primary" data-action="save-memory">Ajouter au canon</button><button class="btn btn-ghost" data-action="close-modal">Annuler</button></div>');
    },
    'save-memory': () => {
      const cat = (document.getElementById('mem-cat') || {}).value || 'Fait';
      const fact = (document.getElementById('mem-fact') || {}).value || '';
      if (!fact.trim()) { T.toast('Le fait ne peut pas être vide', 'error'); return; }
      const p = T.project();
      const list = T.canon.detectMemory({ id: T.uid('mem'), category: cat, fact: fact.trim() }, p);
      confirmWithContradictions('Ajouter ce fait au canon', list, () => {
        addMemory(p, cat.trim() || 'Fait', fact.trim(), 'Fait ajouté manuellement.');
        T.save(); closeModal(); render(); T.toast('Fait ajouté au canon', 'ok');
      }, 'Ajouter quand même');
    },
    'del-memory': (el) => { T.update((p) => { p.canon.memories = (p.canon.memories || []).filter((m) => m.id !== el.dataset.id); }); render({ keepScroll: true }); },

    'test-seed': () => {
      const p = T.project();
      const aiden = {
        id: 'char_aiden_canon', name: 'Aiden', role: 'Protagoniste', archetype: 'Mage d’Élite Réticent',
        personality: 'Calme, loyal, hanté par une erreur ancienne.',
        physicalAppearance: 'Jeune homme aux cheveux noirs ébouriffés et yeux bleus électriques',
        distinctiveFeatures: 'Cicatrice au sourcil gauche', characteristicClothing: 'Manteau de mage rapiécé',
        abilities: 'Magie de foudre', relationships: 'Rival de Kael', plannedArc: 'Accepter son héritage',
        currentState: 'En fuite', isValidated: true
      };
      p.canon.characters = (p.canon.characters || []).filter((c) => c.name !== 'Aiden').concat([aiden]);
      addMemory(p, 'Personnage', 'Aiden est le Protagoniste, cheveux noirs, yeux bleus.', 'Continuité visuelle stricte.');
      T.ui.contradictions = [];
      T.save(); render(); T.toast('Aiden établi dans le canon', 'ok');
    },
    'test-memory': () => {
      const p = T.project();
      addMemory(p, 'Mort', 'Aiden meurt au chapitre 3 et son corps est détruit.', 'Aucune réintroduction possible sans résurrection explicite.');
      T.save(); render(); T.toast('Fait absolu enregistré', 'ok');
    },
    'test-role': () => {
      const p = T.project();
      const src = (p.canon.characters || []).find((c) => c.name === 'Aiden');
      if (!src) { T.toast('Insérez d’abord Aiden au canon', 'error'); return; }
      T.ui.contradictions = T.canon.detectCharacter(Object.assign({}, src, { id: 'test_role', role: 'Antagoniste' }), p);
      T.save(); render();
    },
    'test-hair': () => {
      const p = T.project();
      const src = (p.canon.characters || []).find((c) => c.name === 'Aiden');
      if (!src) { T.toast('Insérez d’abord Aiden au canon', 'error'); return; }
      T.ui.contradictions = T.canon.detectCharacter(Object.assign({}, src, { id: 'test_hair', physicalAppearance: 'Jeune homme aux cheveux roux et yeux bleus' }), p);
      T.save(); render();
    },
    'test-death': () => {
      const p = T.project();
      const newAiden = { id: 'test_death', name: 'Aiden', role: 'Protagoniste', physicalAppearance: 'cheveux noirs, yeux bleus', personality: 'Déterminé', plannedArc: 'Venger sa sœur' };
      T.ui.contradictions = T.canon.detectCharacter(newAiden, p);
      T.save(); render();
    },
    'test-cover': () => {
      const p = T.project();
      T.ui.contradictions = T.canon.detectCover({ title: 'Test', visualDescription: 'Couverture avec le titre écrit en lettres dorées sur l’image' }, p);
      T.save(); render();
    },

    'export-json': () => {
      const p = T.project();
      download('toonia-' + T.slug(p.title) + '.json', JSON.stringify(p, null, 2), 'application/json');
      T.toast('JSON du projet téléchargé', 'ok');
    },
    'export-md': () => {
      const p = T.project();
      download('toonia-' + T.slug(p.title) + '.md', projectMarkdown(p), 'text/markdown;charset=utf-8');
      T.toast('Scénario Markdown téléchargé', 'ok');
    },
    'export-all': () => exportAll(),
    print: () => window.print(),

    'test-key': () => {
      (async () => {
        T.toast('Test du moteur en cours…');
        try {
          await T.ai.testKey();
          T.toast('Connexion Gemini réussie', 'ok');
        } catch (e) {
          T.toast('Échec : ' + (e.message || 'moteur indisponible'), 'error');
        }
      })();
    },
    'check-engine': () => {
      (async () => {
        await T.ai.checkServer();
        render({ keepScroll: true });
        T.toast(T.ai.hasServerKey() ? 'Moteur prêt · TOON_IA détectée' : 'TOON_IA introuvable sur ce déploiement', T.ai.hasServerKey() ? 'ok' : 'error');
      })();
    },
    copy: async (el) => {
      const text = el.dataset.copy || '';
      try {
        await navigator.clipboard.writeText(text);
        T.toast('Copié dans le presse-papiers', 'ok');
      } catch (_) {
        const ta = document.createElement('textarea');
        ta.value = text; document.body.appendChild(ta); ta.select();
        try { document.execCommand('copy'); T.toast('Copié', 'ok'); } catch (e) { T.toast('Copie impossible', 'error'); }
        ta.remove();
      }
    },
    'scene-select': () => {}
  };

  /* ---------------- événements ---------------- */
  document.addEventListener('click', (ev) => {
    const el = ev.target.closest('[data-action]');
    if (!el) return;
    const action = el.dataset.action;
    const isHashLink = el.tagName === 'A' && /^#/.test(el.getAttribute('href') || '');
    if (!isHashLink) ev.preventDefault();

    // les cases à cocher sont traitées par l'événement change (évite le double rendu)
    if (el.type === 'checkbox' && action === 'toggle-chibi') return;

    if (action === 'retry-last' || action === 'confirm-pending') {
      const fn = actions[action];
      if (fn) fn(el);
      return;
    }
    if (action === 'really-reset' || action === 'really-delete-project') {
      const fn = actions[action];
      if (fn) fn(el);
      return;
    }

    // checkbox actions (change event also handled)
    if (action === 'toggle-chibi') {
      const fn = actions[action];
      if (fn) fn(el);
      return;
    }

    // async actions
    if (['analyze', 'gen-all', 'gen-characters', 'gen-world', 'gen-story', 'gen-visual', 'gen-chapter', 'gen-detail', 'gen-panels', 'gen-dialogues', 'gen-marketing'].includes(action)) {
      const fn = actions[action];
      if (fn) fn(el);
      return;
    }

    // input type checkbox / select with data-action
    const fn = actions[action];
    if (fn) fn(el);
  });

  document.addEventListener('change', (ev) => {
    const el = ev.target;

    if (el.id === 'project-select') {
      T.state.activeId = el.value;
      T.ui = { sceneId: null, canonTab: 'overview', contradictions: [] };
      T.lastError = null;
      T.save();
      render();
      return;
    }

    if (el.dataset && el.dataset.action === 'scene-select') {
      T.ui.sceneId = el.value;
      render();
      return;
    }

    if (el.dataset && el.dataset.action === 'toggle-chibi') { actions['toggle-chibi'](el); return; }

    if (el.dataset && el.dataset.bind && el.dataset.kind === 'select') {
      applyBinding(el);
    }
  });

  let saveTimer = null;
  function scheduleSave() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => T.saveQuiet(), 350);
  }

  function applyBinding(el) {
    const path = el.dataset.bind;
    const value = el.type === 'checkbox' ? !!el.checked : el.value;
    if (path.indexOf('settings.') === 0) {
      T.setPath(T.state, path, value);
      T.save();
      if (path === 'settings.model') render({ keepScroll: true });
      return;
    }
    const p = T.project();
    if (!p) return;
    T.setPath(p, path, value);
    p.updatedAt = T.now();
    scheduleSave();
  }

  document.addEventListener('input', (ev) => {
    const el = ev.target;
    if (el.dataset && el.dataset.bind && el.dataset.kind !== 'select') applyBinding(el);
  });

  document.addEventListener('keydown', (ev) => {
    if (ev.key === 'Escape') { closeModal(); setSidebar(false); }
    // ⌘/Ctrl + Entrée dans le brief = lancer la génération complète.
    if ((ev.metaKey || ev.ctrlKey) && ev.key === 'Enter') {
      const el = ev.target;
      if (el && el.dataset && el.dataset.bind === 'idea') {
        ev.preventDefault();
        const btn = document.querySelector('[data-action="gen-all"]:not([disabled])');
        if (btn) btn.click();
        else T.toast('Brief trop court (40 caractères minimum) ou moteur indisponible.', 'error');
      }
    }
    if ((ev.metaKey || ev.ctrlKey) && ev.key === 's') {
      ev.preventDefault();
      T.save();
      T.toast('Projet sauvegardé', 'ok');
    }
  });

  window.addEventListener('hashchange', () => {
    if (!restoringScroll) render();
  });

  /* ---------------- démarrage ---------------- */
  function boot() {
    T.load();
    T.ai.normalizeModel();
    T.ui = { sceneId: null, canonTab: 'overview', contradictions: [] };
    if (!location.hash) location.hash = '#/accueil';
    render();
    // Détecte le proxy serveur (clé en variable d'environnement Vercel).
    T.ai.checkServer().then(() => render({ keepScroll: true })).catch(() => {});
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
