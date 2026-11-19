/* =========================================================
   ToonIA Studio — core.js
   État, persistance locale, moteur de démonstration,
   client Gemini, visuels procéduraux, moteur canonique.
   ========================================================= */
(function () {
  'use strict';

  const T = (window.T = {});

  /* ---------------- utilitaires ---------------- */
  T.uid = (p = 'id') => p + '_' + Math.random().toString(36).slice(2, 10);
  T.esc = (s) =>
    String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  T.clamp = (s, n) => {
    const str = String(s == null ? '' : s).trim();
    return str.length > n ? str.slice(0, n - 1) + '…' : str;
  };
  T.slug = (s) =>
    String(s || 'projet')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 48) || 'projet';

  function hashStr(str) {
    let h = 2166136261 >>> 0;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return h >>> 0;
  }
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  T.rngFor = (seed) => mulberry32(hashStr(String(seed || 'toonia')));
  T.pick = (rng, arr) => arr[Math.floor(rng() * arr.length) % arr.length];
  T.rotate = (rng, arr, n) => {
    const start = Math.floor(rng() * arr.length) % arr.length;
    return Array.from({ length: Math.min(n, arr.length) }, (_, i) => arr[(start + i) % arr.length]);
  };
  T.now = () => Date.now();

  /* ---------------- profils de genre ---------------- */
  const GENRES = [
    {
      id: 'cyberpunk',
      keys: ['cyber', 'punk', 'implant', 'néon', 'neon', 'hacker', 'dystop', 'corporat', 'bionique', 'androïd', 'androide', 'futur', 'quantique', 'bionique'],
      label: 'Cyberpunk / Science-fiction',
      tone: 'Sombre, tendu, mélancolique',
      subgenres: ['Dystopie technologique', 'Thriller urbain', 'Post-humanisme'],
      themes: ['la survie', 'la mémoire', 'le transhumanisme', 'la corruption'],
      hero: 'Kael', hero2: 'Mira', antagonist: 'Silas Vorn', mentor: 'Dok Henz',
      archetype: 'Mécanicien clandestin réticent',
      ability: 'Manipulation d’implants et sens augmenté',
      hair: 'noirs ébouriffés', eyes: 'bleus électriques',
      look: 'Perfecto élimé, câbles apparents sur l’avant-bras, masque filtrant',
      places: [
        { name: 'Les Bas-Fonds de Néopolis', desc: 'Dédale de ruelles saturées d’enseignes holographiques et de pluie acide.', atmos: 'Étouffante, scintillante, dangereuse' },
        { name: 'Chimera Dynamics', desc: 'Tour de verre où se décident les vies louées des habitants.', atmos: 'Clinique, froide, écrasante' },
        { name: 'Le Marché des Souvenirs', desc: 'Marché noir où l’on revend des mémoires d’occasion.', atmos: 'Fiévreuse, intime, immorale' }
      ],
      factions: 'Chimera Dynamics (corporation dominante) ; les Récupérateurs (contrebande d’implants) ; la Cellule Zéro (hackers insoumis).',
      rules: 'Toute augmentation consomme une ressource vitale. Un implant prohibé déclenche une traque automatisée en moins de six heures.',
      geography: 'Une ville verticale : les étages hauts respirent un air filtré, les niveaux bas baignent dans les infiltrations.',
      social: 'Citoyens notés par un score de crédit social ; les « sans-score » vivent hors réseau.',
      lore: 'Vingt ans plus tôt, l’effondrement du réseau central a effacé une génération de souvenirs.',
      terms: [{ term: 'Fantôme', def: 'Trace mnésique laissée par un implant retiré trop vite.' }, { term: 'Saignée', def: 'Coupure volontaire du réseau pour échapper à la traque.' }],
      constraints: 'Pas de voyage temporel ; la technologie ne résout jamais un conflit humain.',
      objects: [
        { name: 'L’implant quantique', sig: 'Objet déclencheur : il rembobine cinq secondes au prix du corps.', desc: 'Une puce triangulaire qui pulse comme un cœur malade.' },
        { name: 'Le carnet de Lena', sig: 'Preuve fragile que la version officielle du passé est fausse.', desc: 'Carnet papier tenu par une sœur disparue.' }
      ],
      conflict: 'La ville survit en effaçant la mémoire de ses habitants ; le héros doit choisir entre vérité et survie.',
      stakes: 'Si la vérité éclate, le réseau s’effondre — avec tous ceux qui en dépendent.',
      visual: { artStyle: 'Manhwa cyberpunk contrasté, encrage nerveux', palette: 'Violet néon, cyan glacé, magenta, noir profond', paneling: 'Défilement vertical, cases hautes et étroites, inserts de détail', lighting: 'Contrastes durs, sources artificielles, halos de pluie', elements: 'Pluie, néons, câbles, reflets sur le bitume', framing: 'Plans larges verticaux pour la ville, gros plans sur les yeux', continuity: 'Implants toujours visibles à gauche du visage' },
      humor: { style: 'Ironie cynique, humour de survie', gags: 'Le distributeur qui refuse la monnaie trouée', hooks: 'Mira commente chaque situation comme un tutoriel mal écrit.', situations: 'Négociations avec des criminels trop polis', freq: 'Une réplique toutes les 3 planches' }
    },
    {
      id: 'fantasy',
      keys: ['fantasy', 'magie', 'magique', 'royaume', 'dragon', 'épée', 'epee', 'sorcier', 'elfe', 'chevalier', 'démon', 'demon', 'mythe', 'prophétie', 'prophetie'],
      label: 'Fantasy / Aventure',
      tone: 'Épique, chaleureux, traversé d’ombres',
      subgenres: ['Dark fantasy', 'Quête initiatique', 'Low fantasy'],
      themes: ['le prix du pouvoir', 'la loyauté', 'l’héritage', 'la rédemption'],
      hero: 'Aiden', hero2: 'Naya', antagonist: 'Le Roi Cendré', mentor: 'Mestre Orik',
      archetype: 'Mage d’élite réticent',
      ability: 'Tissage de lumière et lecture des serments',
      hair: 'noirs ébouriffés', eyes: 'bleus électriques',
      look: 'Manteau de voyage rapiécé, gantelet de cuir gravé de runes',
      places: [
        { name: 'La Cité-Sous-Verne', desc: 'Cité bâtie dans les racines d’un arbre-monde fissuré.', atmos: 'Lumineuse, suspendue, inquiète' },
        { name: 'Les Marches de Cendre', desc: 'Plaine grise où les batailles anciennes n’ont jamais pourri.', atmos: 'Silencieuse, sèche, sacrée' },
        { name: 'Le Puits des Serments', desc: 'Puits où chaque promesse prononcée laisse une marque.', atmos: 'Fraîche, vertigineuse, solennelle' }
      ],
      factions: 'L’Ordre du Verne (gardien des serments) ; la Maison Cendrée (usurpateurs) ; les Sans-Nom (mages renégats).',
      rules: 'Un serment prononcé au Puits devient physique : le trahir brûle la peau. La magie se paie en souvenirs.',
      geography: 'Un continent autour de l’Arbre-Monde, relié par des racines praticables.',
      social: 'Noblesse de serment ; les mages sont craints et loués.',
      lore: 'Il y a mille ans, l’Arbre-Monde fut fendu pour arrêter une guerre — personne ne se souvient de qui a frappé.',
      terms: [{ term: 'Serment noué', def: 'Promesse devenue contrainte magique.' }, { term: 'Cendre vive', def: 'Résidu magique qui garde la chaleur des morts.' }],
      constraints: 'Aucune résurrection ; la magie ne crée jamais de matière, elle déplace.',
      objects: [
        { name: 'La Lame du Premier Serment', sig: 'Arme et preuve dynastique.', desc: 'Épée dont la garde porte un serment illisible.' },
        { name: 'Le Registre des Noms', sig: 'Quiconque y est rayé disparaît des mémoires.', desc: 'Livre relié de cuir clair, anormalement lourd.' }
      ],
      conflict: 'Le royaume tient par des serments que plus personne ne comprend ; le héros hérite du plus dangereux.',
      stakes: 'Si l’Arbre-Monde meurt, les serments se brisent et la magie se retourne contre les vivants.',
      visual: { artStyle: 'Webtoon fantasy semi-réaliste, textures d’aquarelle', palette: 'Vert profond, or pâle, ambre, nuit bleue', paneling: 'Cases amples, respirations paysagères', lighting: 'Lumière filtrée par les feuilles, contre-jours dorés', elements: 'Feuilles, runes, poussière en suspension', framing: 'Plans larges pour les lieux, cadrages hauts pour l’autorité', continuity: 'Runes du gantelet toujours sur l’avant-bras droit' },
      humor: { style: 'Humour de compagnonnage, autodérision', gags: 'Le cheval qui refuse obstinément de traverser l’eau', hooks: 'Naya transforme chaque prophétie en problème administratif.', situations: 'Conseils de guerre absurdes, marchandage avec des esprits', freq: 'Une respiration comique par scène' }
    },
    {
      id: 'romance',
      keys: ['romance', 'amour', 'amoureux', 'lycée', 'lycee', 'collège', 'coeur', 'cœur', 'sentiment', 'couple', 'romantique', 'rupture'],
      label: 'Romance contemporaine',
      tone: 'Doux, lumineux, nuancé de mélancolie',
      subgenres: ['Slow burn', 'Romance scolaire', 'Tranche de vie'],
      themes: ['la confiance', 'le pardon', 'l’image de soi', 'les non-dits'],
      hero: 'Léa', hero2: 'Noah', antagonist: 'Camille', mentor: 'Mme Ravel',
      archetype: 'Perfectionniste qui s’efface',
      ability: 'Écoute et mémoire des détails',
      hair: 'bruns bouclés', eyes: 'marron chaleureux',
      look: 'Chemise trop grande, carnet corné, bracelet offert',
      places: [
        { name: 'Le toit du lycée', desc: 'Espace interdit où l’on vient respirer loin du bruit.', atmos: 'Paisible, dorée, secrète' },
        { name: 'La librairie du canal', desc: 'Ancienne péniche transformée en librairie de quartier.', atmos: 'Chaleureuse, poussiéreuse, rassurante' },
        { name: 'Le quai des brumes', desc: 'Quai de béton où le fleuve avale les confidences.', atmos: 'Grise, intime, suspendue' }
      ],
      factions: 'Le club de théâtre (refuge) ; le groupe de musique de Noah ; la famille de Léa (pression scolaire).',
      rules: 'Aucun drame artificiel : chaque conflit vient d’un besoin non dit, jamais d’un malentendu commode.',
      geography: 'Un quartier de ville moyenne, autour d’un canal et d’un lycée.',
      social: 'Réputation scolaire très surveillée ; les apparences pèsent plus que les faits.',
      lore: 'Un été de rupture a laissé un silence de deux ans entre les deux personnages.',
      terms: [{ term: 'La liste', def: 'Liste de choses à faire avant la fin du lycée, écrite à deux.' }],
      constraints: 'Pas de triangle amoureux toxique ; les émotions doivent être explicites ou montrées.',
      objects: [
        { name: 'Le carnet partagé', sig: 'Un seul carnet circule entre eux, chacun n’écrit qu’une page sur deux.', desc: 'Carnet à spirale, couverture tachée de café.' },
        { name: 'Le bracelet de Léa', sig: 'Preuve d’un lien que personne n’ose nommer.', desc: 'Fine tresse de cuir avec une perle bleue.' }
      ],
      conflict: 'Léa protège son avenir scolaire en s’interdisant d’aimer ; Noah protège sa musique en refusant de rester.',
      stakes: 'Choisir entre la personne qu’on veut être et celle qu’on attend de vous.',
      visual: { artStyle: 'Romance pastel, trait doux et lumineux', palette: 'Rose poudré, crème, bleu ciel, terracotta', paneling: 'Cases horizontales, beaucoup d’air, inserts de mains', lighting: 'Heure dorée, lumière de fin d’après-midi', elements: 'Pétales, pluie fine, reflets du canal', framing: 'Gros plans sur les regards, cadrages derrière l’épaule', continuity: 'Bracelet visible à chaque scène de Léa' },
      humor: { style: 'Humour tendre, gêne adorable', gags: 'Les messages réécrits huit fois avant envoi', hooks: 'Noah cite des paroles de chansons comme s’il parlait normalement.', situations: 'Répétitions de théâtre catastrophiques, malentendus de librairie', freq: 'Léger, une scène sur deux' }
    },
    {
      id: 'horreur',
      keys: ['horreur', 'peur', 'monstre', 'fantôme', 'fantome', 'zombie', 'malédiction', 'malediction', 'sang', 'cauchemar', 'mystérieux', 'mysterieux', 'disparition', 'hanté', 'hante'],
      label: 'Horreur / Mystère',
      tone: 'Anxiogène, silencieux, oppressant',
      subgenres: ['Horreur psychologique', 'Thriller rural', 'Surnaturel lent'],
      themes: ['la culpabilité', 'le secret de famille', 'la contagion', 'le déni'],
      hero: 'Iris', hero2: 'Tomas', antagonist: 'La Veilleuse', mentor: 'Père Aubin',
      archetype: 'Témoin que personne ne croit',
      ability: 'Perception des détails que les autres refusent de voir',
      hair: 'blancs prématurés', eyes: 'gris délavé',
      look: 'Manteau de pluie trop grand, lampe torche, bandage au poignet',
      places: [
        { name: 'La maison des Dormeurs', desc: 'Maison de village dont toutes les fenêtres sont condamnées de l’intérieur.', atmos: 'Humide, immobile, respirante' },
        { name: 'Le pré aux cloches', desc: 'Champ de cloches enterrées qui sonnent sans vent.', atmos: 'Blême, brumeuse, silencieuse' },
        { name: 'Le couloir des archives', desc: 'Sous-sol municipal où les registres de naissance manquent une année entière.', atmos: 'Étouffante, poussiéreuse, menaçante' }
      ],
      factions: 'Le conseil du village (silence organisé) ; les Dormeurs (ceux qui ont vu et accepté) ; la Veilleuse (présence).',
      rules: 'La chose ne franchit jamais un seuil sans y avoir été invitée. Ce qui est vu ne peut plus être oublié.',
      geography: 'Un village isolé de 300 habitants, coupé par la crue deux mois par an.',
      social: 'Tout le monde sait ; personne ne parle. La politesse est une forme de terreur.',
      lore: 'L’année 1979 manque aux registres, et douze familles portent le même deuil.',
      terms: [{ term: 'Veille', def: 'Nuit où une famille doit garder une lumière allumée.' }, { term: 'Dormeur', def: 'Habitant ayant accepté de ne plus voir.' }],
      constraints: 'Aucun jumpscare gratuit ; la menace ne se montre jamais entièrement.',
      objects: [
        { name: 'La cloche de bronze', sig: 'Objet qui rend le rituel possible et le condamne.', desc: 'Cloche fendue au bord coupant.' },
        { name: 'Le dossier 1979', sig: 'La preuve que la mémoire du village a été fabriquée.', desc: 'Chemise cartonnée brûlée sur un coin.' }
      ],
      conflict: 'Iris enquête sur une disparition que la ville a décidé de ne pas voir, et découvre son propre rôle dans l’oubli.',
      stakes: 'Si elle parle, la chose s’intéresse à elle ; si elle se tait, elle devient complice.',
      visual: { artStyle: 'Horreur encrée, noirs profonds, grain analogique', palette: 'Blanc cassé, vert maladif, rouille, encre', paneling: 'Cases resserrées, vides verticaux, ellipses', lighting: 'Lumière rasante, halos faibles, ombres longues', elements: 'Brouillard, poussière, insectes, pluie', framing: 'Plans fixes et caméras hautes, hors-champ systématique', continuity: 'Bandage au poignet toujours présent' },
      humor: { style: 'Humour noir très rare, soupape de tension', gags: 'Le chat du presbytère qui juge tout le monde', hooks: 'Tomas fait une blague inappropriée chaque fois qu’il a peur.', situations: 'Repas de village poliment sinistres', freq: 'Une par chapitre maximum' }
    },
    {
      id: 'action',
      keys: ['shonen', 'combat', 'tournoi', 'héros', 'heros', 'pouvoir', 'super', 'école', 'ecole', 'guilde', 'chasseur', 'niveau', 'donjon', 'art martial'],
      label: 'Action / Shonen',
      tone: 'Énergique, franc, généreux',
      subgenres: ['Nekketsu', 'Tournoi', 'Progression de puissance'],
      themes: ['le dépassement', 'l’amitié', 'la justice', 'la rivalité'],
      hero: 'Ren', hero2: 'Sora', antagonist: 'Kurogane', mentor: 'Maître Ito',
      archetype: 'Combattant instinctif sous-estimé',
      ability: 'Absorption d’impact et contre-attaque instantanée',
      hair: 'roux en bataille', eyes: 'ambre doré',
      look: 'Veste d’entraînement déchirée, bandages aux poings',
      places: [
        { name: 'L’arène des Trois Lunes', desc: 'Colisée suspendu où se jouent les qualifications.', atmos: 'Électrique, poussiéreuse, bruyante' },
        { name: 'Le dojo effondré', desc: 'Ruine d’un dojo qui fut la fierté du quartier.', atmos: 'Nostalgique, silencieuse, brûlante' },
        { name: 'La faille du Nord', desc: 'Frontière où apparaissent les créatures de rang supérieur.', atmos: 'Glaciale, instable, vertigineuse' }
      ],
      factions: 'La Guilde des Porteurs (ordre officiel) ; les Sans-Rang (combattants clandestins) ; le Clan Kurogane.',
      rules: 'Un pouvoir s’éveille seulement après avoir été poussé au-delà de sa limite, et laisse une séquelle permanente.',
      geography: 'Un archipel de cités-États reliées par des portails surveillés.',
      social: 'Le rang détermine tout : logement, salaire, droit de combattre.',
      lore: 'Les portails sont apparus il y a trente ans ; la première génération de Porteurs a payé le prix.',
      terms: [{ term: 'Seuil', def: 'Moment où un pouvoir se révèle.' }, { term: 'Séquelle', def: 'Prix physique permanent d’un dépassement.' }],
      constraints: 'Pas de puissance gagnée hors écran ; chaque progression se paie.',
      objects: [
        { name: 'Les bandages du Maître', sig: 'Héritage moral et technique.', desc: 'Bandes de tissu usées, nouées d’un nœud impossible à refaire.' },
        { name: 'La clé de faille', sig: 'Permet d’ouvrir un portail interdit.', desc: 'Éclat de métal froid qui vibre près des portails.' }
      ],
      conflict: 'Ren veut prouver sa valeur sans rang, dans un système qui l’a déjà condamné.',
      stakes: 'La faille du Nord s’élargit : chaque tournoi distrait la population d’une menace réelle.',
      visual: { artStyle: 'Manhwa d’action, lignes de vitesse franches', palette: 'Orange brûlé, jaune, bleu nuit, blanc', paneling: 'Cases dynamiques, sauts de taille, gros plans d’impact', lighting: 'Contre-jours héroïques, éclairs, poussière lumineuse', elements: 'Étincelles, débris, onomatopées', framing: 'Plongées pour l’écrasement, contre-plongées pour la révélation', continuity: 'Bandages aux poings dans chaque combat' },
      humor: { style: 'Comédie de groupe, gags de rivalité', gags: 'Le classement affiché qui contredit tout le monde', hooks: 'Sora commente chaque combat comme un match de football.', situations: 'Entraînements absurdes, repas de guilde chaotiques', freq: 'Une scène légère par chapitre' }
    },
    {
      id: 'mystere',
      keys: ['mystère', 'mystere', 'enquête', 'enquete', 'détective', 'detective', 'crime', 'meurtre', 'secret', 'disparu', 'énigme', 'enigme', 'polar'],
      label: 'Thriller / Enquête',
      tone: 'Sec, précis, sous tension',
      subgenres: ['Polar procédural', 'Mystère de chambre close', 'Conspiration'],
      themes: ['la vérité', 'la corruption', 'la loyauté', 'l’aveuglement'],
      hero: 'Nora', hero2: 'Elias', antagonist: 'Le Commanditaire', mentor: 'Commissaire Bréal',
      archetype: 'Enquêtrice méthodique en disgrâce',
      ability: 'Reconstitution mentale des scènes',
      hair: 'bruns courts', eyes: 'verts attentifs',
      look: 'Trench fatigué, carnet noir, stylo toujours à la même poche',
      places: [
        { name: 'Le commissariat du 9e', desc: 'Bâtiment administratif où les dossiers sensibles s’évaporent.', atmos: 'Néons blafards, café brûlé, silence' },
        { name: 'L’hôtel Mirador', desc: 'Hôtel de luxe où la victime a été vue pour la dernière fois.', atmos: 'Feutrée, dorée, mensongère' },
        { name: 'Les docks Sud', desc: 'Zone portuaire où les caméras ne fonctionnent jamais.', atmos: 'Humide, dangereuse, anonyme' }
      ],
      factions: 'La brigade du 9e (officiel) ; le cabinet Mirador (pouvoir économique) ; les dockers (informateurs).',
      rules: 'Chaque indice est vérifiable ; aucun coupable ne repose sur une intuition non étayée.',
      geography: 'Une ville portuaire coupée en deux par un fleuve et un contrat de rénovation.',
      social: 'Une ville où l’on connaît tout le monde et où l’on se doit des services.',
      lore: 'Une affaire classée il y a douze ans refait surface par un détail identique.',
      terms: [{ term: 'Le dossier 12', def: 'Affaire non résolue qui relie tous les suspects.' }],
      constraints: 'Pas de coupable caché introduit après le dernier tiers.',
      objects: [
        { name: 'La clé de chambre 402', sig: 'Ouvre une porte qui n’existe plus sur le plan.', desc: 'Clé magnétique au numéro limé.' },
        { name: 'Le carnet noir', sig: 'Notes personnelles de l’enquêtrice, preuves de ses doutes.', desc: 'Carnet à élastique, pages cornées.' }
      ],
      conflict: 'Nora doit prouver un crime que sa hiérarchie a intérêt à enterrer.',
      stakes: 'Si elle échoue, un innocent tombe et le vrai coupable obtient le contrat du siècle.',
      visual: { artStyle: 'Thriller réaliste, encrage sec, tramage discret', palette: 'Gris bleuté, ambre, blanc froid, rouge signal', paneling: 'Cases régulières, inserts d’indices, doubles pages de reconstitution', lighting: 'Lampadaires, néons, clair-obscur', elements: 'Pluie, reflets, papiers, fumée', framing: 'Cadrages documentaires, légère contre-plongée pour le pouvoir', continuity: 'Carnet noir et stylo toujours visibles' },
      humor: { style: 'Humour pince-sans-rire', gags: 'La machine à café comme personnage secondaire', hooks: 'Elias cite des procédures inexistantes avec un sérieux total.', situations: 'Interrogatoires qui dérapent en règlements de comptes', freq: 'Une réplique sèche toutes les deux scènes' }
    }
  ];

  const DEFAULT_GENRE = GENRES[1];

  function detectProfile(project) {
    const idea = ((project && project.idea) || '').toLowerCase();
    const adv = (project && project.advanced) || {};
    const hay = [idea, adv.genre || '', adv.ambiance || '', adv.inspirations || ''].join(' ').toLowerCase();
    let best = DEFAULT_GENRE, bestScore = 0;
    for (const g of GENRES) {
      let score = 0;
      for (const k of g.keys) if (hay.includes(k)) score += 1;
      if (score > bestScore) { bestScore = score; best = g; }
    }
    return best;
  }
  T.profile = detectProfile;

  const COMMON_WORDS = new Set(['dans', 'une', 'avec', 'pour', 'mais', 'elle', 'ils', 'elles', 'nous', 'vous', 'cette', 'après', 'quand', 'chaque', 'leur', 'leurs', 'comme', 'tout', 'tous', 'plus', 'sans', 'sous', 'entre', 'alors', 'ainsi', 'depuis', 'avant', 'encore', 'même', 'aussi', 'histoire', 'jeune', 'monde', 'ville', 'projet', 'webtoon', 'héros', 'heros']);

  function extractNames(idea, profile) {
    const names = [];
    const rx = /\b([A-ZÀ-Ý][a-zà-ÿ]{2,13})\b/g;
    let m;
    while ((m = rx.exec(idea || ''))) {
      const w = m[1];
      if (COMMON_WORDS.has(w.toLowerCase())) continue;
      if (names.includes(w)) continue;
      const before = (idea || '').slice(Math.max(0, m.index - 2), m.index);
      if (before === '' && names.length === 0 && w === (idea || '').trim().split(/\s+/)[0]) continue; // premier mot de phrase
      names.push(w);
      if (names.length >= 3) break;
    }
    return names;
  }

  function firstSentence(text, fallback) {
    const clean = String(text || '').replace(/\s+/g, ' ').trim();
    if (!clean) return fallback;
    const cut = clean.split(/(?<=[.!?])\s/)[0];
    return clean.length > 12 ? cut : fallback;
  }

  /* ---------------- état & persistance ---------------- */
  const STORE_KEY = 'toonia.web.v1';

  T.blankProject = function (title, idea) {
    return {
      id: T.uid('proj'),
      title: title || 'Nouveau webtoon',
      idea: idea || '',
      advanced: {},
      analysis: null,
      characters: [],
      world: null,
      story: null,
      visual: null,
      humor: null,
      chapters: [],
      marketing: null,
      canon: {
        project: null,
        characters: [],
        locations: [],
        chapters: [],
        memories: [],
        cover: null,
        storyBible: null,
        worldBible: null,
        visualBible: null,
        humorDNA: null
      },
      validated: {},
      createdAt: T.now(),
      updatedAt: T.now()
    };
  };

  function seedState() {
    const demo = T.blankProject('Projet démo ToonIA', 'Dans une métropole sous-marine isolée du monde, une cartographe découvre que le dôme commence à se fissurer depuis l’intérieur.');
    return {
      version: 1,
      projects: [demo],
      activeId: demo.id,
      settings: { apiKey: '', model: 'gemini-3.1-flash-lite-preview', demo: false }
    };
  }

  T.state = null;

  T.load = function () {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) { T.state = seedState(); T.save(); return; }
      const parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.projects) || !parsed.projects.length) throw new Error('état invalide');
      T.state = parsed;
      T.state.settings = Object.assign({ apiKey: '', model: 'gemini-3.1-flash-lite-preview', demo: false }, parsed.settings || {});
      if (!T.state.projects.some((p) => p.id === T.state.activeId)) T.state.activeId = T.state.projects[0].id;
    } catch (e) {
      T.state = seedState();
      try { T.save(); } catch (_) {}
    }
  };

  T.save = function () {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(T.state));
      return true;
    } catch (e) {
      T.toast('Sauvegarde impossible : stockage local plein ou bloqué.', 'error');
      return false;
    }
  };

  T.saveQuiet = function () {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(T.state)); } catch (_) {}
  };

  T.project = function () {
    if (!T.state) return null;
    return T.state.projects.find((p) => p.id === T.state.activeId) || T.state.projects[0] || null;
  };

  T.update = function (fn, opts) {
    const p = T.project();
    if (!p) return;
    fn(p);
    p.updatedAt = T.now();
    if (!p.title) p.title = 'Nouveau webtoon';
    T.save();
    if (!opts || !opts.silent) T.emit();
  };

  const listeners = [];
  T.onChange = (fn) => listeners.push(fn);
  T.emit = () => listeners.forEach((fn) => { try { fn(); } catch (_) {} });

  T.toast = function (msg, kind) {
    const wrap = document.getElementById('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast' + (kind === 'error' ? ' is-error' : kind === 'ok' ? ' is-ok' : '');
    el.textContent = msg;
    wrap.appendChild(el);
    setTimeout(() => { el.style.transition = 'opacity .25s'; el.style.opacity = '0'; }, 2600);
    setTimeout(() => el.remove(), 3000);
  };

  /* ---------------- moteur de démonstration ---------------- */
  const Demo = (T.demo = {});

  Demo.analysis = function (p) {
    const g = detectProfile(p);
    const adv = p.advanced || {};
    const names = extractNames(p.idea, g);
    const rng = T.rngFor(p.idea + p.title);
    const explicit = [];
    if (adv.genre) explicit.push('Genre souhaité : ' + adv.genre);
    if (adv.ambiance) explicit.push('Ambiance visée : ' + adv.ambiance);
    if (adv.epoque) explicit.push('Contexte temporel : ' + adv.epoque);
    if (adv.personnagesSouhaites) explicit.push('Personnages demandés : ' + adv.personnagesSouhaites);
    if (adv.inspirations) explicit.push('Inspirations : ' + adv.inspirations);
    if (adv.contraintes) explicit.push('Contrainte narrative : ' + adv.contraintes);
    if (!explicit.length) {
      String(p.idea || '').split(/[.;!?]/).map((s) => s.trim()).filter((s) => s.length > 24).slice(0, 3)
        .forEach((s) => explicit.push('Élément posé par l’auteur : ' + T.clamp(s, 120)));
    }

    const ambiguities = [];
    if (!adv.personnagesSouhaites && names.length === 0) ambiguities.push('Qui est le personnage principal, et que veut-il précisément ?');
    if (!adv.ambiance) ambiguities.push('Quelle ambiance dominante : chaleureuse, inquiétante, épique ?');
    if (!adv.longueurVisee) ambiguities.push('Quelle longueur visée : one-shot, saison courte ou série longue ?');
    if (!adv.publicCible) ambiguities.push('À quel public s’adresse le webtoon (âge, codes de lecture) ?');
    if (!adv.contraintes) ambiguities.push('Quel est le dilemme central, et qu’est-ce qui empêche le héros d’y répondre ?');
    if (ambiguities.length < 2) ambiguities.push('Quel événement déclenche l’histoire au chapitre 1 ?');

    return {
      genrePrincipal: adv.genre || g.label,
      sousGenres: T.rotate(rng, g.subgenres, 3),
      ton: adv.ambiance || g.tone,
      themesDetectes: T.rotate(rng, g.themes, 4),
      elementsNarratifsExplicites: explicit,
      ambiguitesAClarifier: ambiguities.slice(0, 5),
      _genreId: g.id
    };
  };

  function makeCharacters(p) {
    const g = detectProfile(p);
    const names = extractNames(p.idea, g);
    const hero = names[0] || g.hero;
    const ally = names[1] || g.hero2;
    const rng = T.rngFor(p.idea + 'chars');
    const roles = [
      { role: 'Protagoniste', name: hero, arche: g.archetype },
      { role: 'Allié', name: ally, arche: 'Contrepoint pragmatique du héros' },
      { role: 'Antagoniste', name: g.antagonist, arche: 'Gardien d’un ordre injuste' },
      { role: 'Mentor', name: g.mentor, arche: 'Ancien qui a payé le prix' }
    ];
    return roles.map((r, i) => {
      const isHero = i === 0;
      return {
        id: T.uid('char'),
        name: r.name,
        role: r.role,
        archetype: r.arche,
        personality: isHero
          ? 'Tenace, ironique quand il a peur, incapable de laisser une injustice sans réponse. Faiblesse : confond protéger et décider à la place des autres.'
          : r.role === 'Antagoniste'
            ? 'Calme, persuasif, convaincu que sa cruauté est une forme de soin. Faiblesse : ne supporte pas d’être contredit publiquement.'
            : r.role === 'Mentor'
              ? 'Patient, économe en mots, hanté par une erreur ancienne. Faiblesse : préfère le silence à l’aveu.'
              : 'Direct, drôle, loyal jusqu’à l’imprudence. Faiblesse : parle avant de réfléchir dès qu’on touche à ses proches.',
        physicalAppearance: isHero
          ? 'Silhouette sèche, cheveux ' + g.hair + ', yeux ' + g.eyes + ', une cicatrice fine au sourcil gauche.'
          : r.role === 'Antagoniste'
            ? 'Grand, cheveux blancs coupés court, yeux ' + g.eyes + ' presque translucides, visage sans expression.'
            : 'Visage ouvert, cheveux ' + (i === 1 ? 'courts châtains' : 'gris coupés ras') + ', yeux ' + (i === 1 ? 'verts' : 'gris') + ', tient toujours quelque chose dans les mains.',
        distinctiveFeatures: isHero
          ? 'Cicatrice au sourcil gauche, tic consistant à faire craquer son pouce.'
          : r.role === 'Antagoniste'
            ? 'Bague plate à l’index droit, jamais de cravate.'
            : i === 1 ? 'Une boucle d’oreille unique, toujours du même côté.' : 'Boite légèrement, le sait et s’en sert.',
        characteristicClothing: isHero ? g.look : r.role === 'Antagoniste' ? 'Costume sombre sans ornement, gants de cuir fin' : 'Vêtements pratiques, une pièce toujours de travers',
        abilities: isHero ? g.ability : r.role === 'Antagoniste' ? 'Réseau, influence, lecture des faiblesses' : r.role === 'Mentor' ? 'Savoir technique et mémoire longue' : 'Débrouillardise, contacts, sang-froid',
        relationships: isHero
          ? 'Lien de confiance fragile avec ' + g.hero2 + ' ; fascination méfiante pour ' + g.antagonist + '.'
          : r.role === 'Antagoniste'
            ? 'Voit en ' + hero + ' un successeur possible, donc une menace.'
            : 'Protège ' + hero + ' sans toujours le dire.',
        plannedArc: isHero
          ? 'Passer de « sauver tout le monde seul » à « choisir avec qui porter le poids ».'
          : r.role === 'Antagoniste' ? 'Révéler que son ordre protège une chose pire encore.' : 'Sortir de la dette morale envers le passé.',
        currentState: isHero ? 'Débute l’histoire en marge, sans statut ni appui.' : r.role === 'Antagoniste' ? 'Au sommet de son influence, mais contredit pour la première fois.' : 'Vit caché, disponible mais méfiant.',
        isValidated: false
      };
    });
  }

  Demo.characters = function (p) {
    const out = makeCharacters(p);
    const existing = (p.canon && p.canon.characters) || [];
    out.forEach((c) => { c.isValidated = existing.some((x) => x.name.toLowerCase() === c.name.toLowerCase()); });
    return out;
  };

  Demo.world = function (p) {
    const g = detectProfile(p);
    const chars = p.characters && p.characters.length ? p.characters : makeCharacters(p);
    return {
      rules: g.rules,
      factions: g.factions,
      geography: g.geography,
      socialStructure: g.social,
      historyLore: g.lore,
      terminology: g.terms.map((t) => '• ' + t.term + ' — ' + t.def).join('\n'),
      worldConstraints: g.constraints,
      locations: g.places.map((pl, i) => ({ id: T.uid('loc'), name: pl.name, description: pl.desc, atmosphere: pl.atmos, orderIndex: i, isValidated: false })),
      objects: g.objects.map((o, i) => ({ id: T.uid('obj'), name: o.name, significance: o.sig, description: o.desc, ownerCharacterId: chars[i % Math.max(1, chars.length)] ? chars[i % chars.length].id : null, isValidated: false })),
      isValidated: false
    };
  };

  Demo.story = function (p) {
    const g = detectProfile(p);
    const a = p.analysis || Demo.analysis(p);
    const names = (p.characters || []).map((c) => c.name);
    const hero = names[0] || g.hero;
    const ant = names.find((_, i) => (p.characters[i] || {}).role === 'Antagoniste') || g.antagonist;
    return {
      premise: firstSentence(p.idea, g.conflict),
      confirmedGenre: a.genrePrincipal || g.label,
      mainThemes: (a.themesDetectes || g.themes).join(', '),
      generalTone: a.ton || g.tone,
      mainConflicts: 'Interne : ' + hero + ' doit accepter de dépendre des autres.\nRelationnel : la confiance entre ' + hero + ' et ' + (names[1] || g.hero2) + ' se construit puis se brise.\nSociétal : ' + g.conflict,
      mainPlot: 'Acte I — ' + hero + ' découvre que ' + g.stakes.charAt(0).toLowerCase() + g.stakes.slice(1) + '\nActe II — ' + ant + ' propose une solution qui exige un sacrifice inacceptable.\nActe III — ' + hero + ' retourne le prix du sacrifice contre ' + ant + '.',
      subPlots: '• La dette morale du mentor envers le passé.\n• Le passé effacé que chacun préfère ne pas nommer.\n• Le groupe secondaire qui apprend à survivre sans les héros.',
      narrativeDirection: 'Climax : ' + g.stakes + ' Promesse finale : une victoire qui coûte quelque chose de définitif.',
      pacing: 'Rythme en dents de scie : deux chapitres de tension, un chapitre de respiration, une révélation tous les cinq chapitres.',
      timelineEvents: [
        { id: T.uid('evt'), orderIndex: 1, title: 'L’événement fondateur', description: g.lore, dateOrEpoch: 'Il y a plusieurs années', isValidated: false },
        { id: T.uid('evt'), orderIndex: 2, title: 'Le basculement', description: g.conflict, dateOrEpoch: 'Un an avant le chapitre 1', isValidated: false },
        { id: T.uid('evt'), orderIndex: 3, title: 'Le point de rupture', description: 'Ce qui rend le chapitre 1 inévitable : ' + g.stakes, dateOrEpoch: 'Chapitre 1', isValidated: false }
      ],
      isValidated: false
    };
  };

  Demo.visual = function (p) {
    const g = detectProfile(p);
    return Object.assign({ isValidated: false }, g.visual);
  };

  Demo.humor = function (p) {
    const g = detectProfile(p);
    const chars = (p.characters || []).slice(0, 2);
    return {
      humorStyle: g.humor.style,
      chibiMomentsAllowed: true,
      runningGags: g.humor.gags,
      punchlineFrequency: g.humor.freq,
      characterHumorHooks: chars.map((c) => '• ' + c.name + ' : ' + g.humor.hooks).join('\n') || '• ' + g.hero + ' : ' + g.humor.hooks,
      comicSituations: g.humor.situations,
      isValidated: false
    };
  };

  Demo.chapter = function (p, number) {
    const g = detectProfile(p);
    const chars = (p.characters || []).map((c) => c.name);
    const hero = chars[0] || g.hero;
    const ally = chars[1] || g.hero2;
    const ant = chars.find((_, i) => (p.characters[i] || {}).role === 'Antagoniste') || g.antagonist;
    const locs = ((p.world && p.world.locations) || g.places.map((x) => ({ name: x.name })));
    const a = p.analysis || Demo.analysis(p);
    const num = number || 1;
    return {
      id: T.uid('chap'),
      number: num,
      title: num === 1 ? 'Le seuil' : 'Chapitre ' + num,
      synopsis: 'Ce que ' + hero + ' croyait stable se fissure : ' + g.stakes.charAt(0).toLowerCase() + g.stakes.slice(1),
      isCanon: false,
      scenes: [
        {
          id: T.uid('scene'), orderIndex: 1, title: 'Le monde normal',
          locationName: locs[0].name || g.places[0].name,
          charactersPresent: [hero, ally],
          summary: hero + ' vaque à ses occupations en masquant un problème qu’il croit personnel. ' + ally + ' remarque le détail que personne d’autre ne voit.',
          beats: ['Ouverture sur un geste quotidien devenu inquiétant.', 'Le dialogue anodin qui dit tout de travers.', 'Un indice que le spectateur comprend avant le héros.'],
          atmosphere: g.places[0].atmos, emotionalArc: 'Banalité → tension sourde', pacingNotes: 'Cases larges, peu de texte, laisser respirer.',
          detail: null, panels: [], isValidated: false
        },
        {
          id: T.uid('scene'), orderIndex: 2, title: 'L’irruption',
          locationName: locs[1] ? locs[1].name : g.places[1].name,
          charactersPresent: [hero, ant],
          summary: ant + ' apparaît avec une proposition raisonnable qui contient une menace implicite. ' + hero + ' refuse trop vite et révèle son point faible.',
          beats: ['Arrivée décalée, cadrée de loin.', 'La proposition formulée comme un service.', 'Le refus qui sonne comme un aveu.'],
          atmosphere: g.places[1].atmos, emotionalArc: 'Surprise → colère contenue', pacingNotes: 'Champ/contrechamp serré, ellipses courtes.',
          detail: null, panels: [], isValidated: false
        },
        {
          id: T.uid('scene'), orderIndex: 3, title: 'La décision',
          locationName: locs[2] ? locs[2].name : g.places[2].name,
          charactersPresent: [hero],
          summary: hero + ' retourne seul sur les lieux et prend la décision qui engage tout le récit : ' + g.conflict,
          beats: ['Plan large qui isole le personnage.', 'L’objet symbolique change de main.', 'Dernière case : regard caméra ou hors-champ.'],
          atmosphere: g.places[2].atmos, emotionalArc: 'Doute → résolution froide', pacingNotes: 'Une grande case finale, presque sans dialogue.',
          detail: null, panels: [], isValidated: false
        }
      ],
      _genre: (a && a._genreId) || g.id
    };
  };

  Demo.sceneDetail = function (p, scene) {
    const g = detectProfile(p);
    return {
      enrichedSummary: scene.summary + ' Le cadrage privilégie l’isolement du personnage, puis la proximité de l’objet qui déclenche la scène.',
      pacingNotes: scene.pacingNotes || 'Alterner cases larges et inserts rapides.',
      emotionalArc: scene.emotionalArc || 'Neutre → décision',
      detailedNarrativeBeats: (scene.beats || []).concat([
        'Insert sur les mains : ce que le personnage ne dit pas.',
        'Un tiers coupe la scène trop tôt, laissant la réplique en suspens.'
      ]),
      atmosphereAndSensory: scene.atmosphere + '. Sons proches, arrière-plan légèrement flou, poussière visible dans la lumière.',
      characterInteractions: 'La distance physique raconte le rapport de force. Aucun contact visuel direct jusqu’au dernier beat.'
    };
  };

  Demo.panels = function (p, scene) {
    const g = detectProfile(p);
    const chars = scene.charactersPresent || [];
    const hero = chars[0] || g.hero;
    const other = chars[1] || '';
    const place = scene.locationName || g.places[0].name;
    const rng = T.rngFor(scene.id + 'panels');
    const cameras = ['Plan large', 'Plan moyen', 'Gros plan', 'Contre-plongée', 'Plongée', 'Plan d’ensemble'];
    const emotions = ['Tension', 'Détermination', 'Surprise', 'Méfiance', 'Tristesse contenue'];
    const actions = [
      hero + ' s’arrête net au milieu du passage, une main déjà levée.',
      'Le visage de ' + hero + ' se ferme à la seconde où ' + (other || 'l’interlocuteur') + ' prononce le nom interdit.',
      'Insert : l’objet passe de main en main, trop lentement pour être anodin.',
      'Le décor se révèle derrière ' + hero + ', plus vaste et plus menaçant que prévu.'
    ];
    const sfx = ['BAM', 'TAP', 'CRRR', 'SOUFFLE', 'CLAC'];
    return actions.map((act, i) => {
      const cam = cameras[(i + Math.floor(rng() * 3)) % cameras.length];
      const emo = emotions[i % emotions.length];
      return {
        id: T.uid('panel'),
        orderIndex: i + 1,
        charactersPresent: i === 0 ? hero : chars.join(' et '),
        location: place,
        actionDescription: act,
        dominantEmotion: emo,
        cameraAngle: cam,
        composition: i === 0 ? 'Personnage décentré à droite, espace négatif à gauche' : 'Ligne de fuite vers le fond, personnage sur le tiers gauche',
        lightingAndAtmosphere: 'Lumière ' + (i % 2 ? 'rasante et froide' : 'diffuse et chaude') + ', contraste marqué, ' + g.places[i % g.places.length].atmos.toLowerCase(),
        backgroundDetails: g.places[i % g.places.length].desc,
        visualEffects: i === 2 ? 'Traînée de mouvement discrète, particules en suspension' : 'Tramage dense dans les ombres, halo léger',
        visualPrompt: 'Webtoon panel, ' + g.visual.artStyle + ', ' + cam.toLowerCase() + ', ' + act + ' Lieu : ' + place + '. Ambiance : ' + g.places[i % g.places.length].atmos + '. Palette : ' + g.visual.palette + '. Éclairage : ' + g.visual.lighting + '. Sans aucun texte, sans bulle, sans logo.',
        continuityNotes: i === 0 ? 'Premier panel de la scène : établir la tenue exacte.' : 'Conserver la tenue et l’état de ' + hero + ' du panel ' + i + '.',
        soundEffects: i === 2 ? T.pick(rng, sfx) : null,
        artSeed: scene.id + ':' + i,
        isValidated: false
      };
    });
  };

  Demo.dialogues = function (p, panel) {
    const g = detectProfile(p);
    const chars = String(panel.charactersPresent || '').split(/ et |,/).map((s) => s.trim()).filter(Boolean);
    const hero = chars[0] || g.hero;
    const other = chars[1] || g.antagonist;
    return [
      { id: T.uid('dlg'), speakerName: other, type: 'SPEECH', emotion: panel.dominantEmotion || 'Neutre', placement: 'TOP_RIGHT', text: 'Tu crois encore que c’est toi qui décides ?' },
      { id: T.uid('dlg'), speakerName: hero, type: 'THOUGHT', emotion: 'Méfiance', placement: 'TOP_LEFT', text: 'Il sait. Il sait depuis le début.' },
      { id: T.uid('dlg'), speakerName: hero, type: 'SHOUT', emotion: 'Colère', placement: 'BOTTOM_LEFT', text: 'Alors dis-le clairement, pour une fois !', soundEffect: panel.soundEffects || 'BAM' }
    ];
  };

  Demo.marketing = function (p) {
    const g = detectProfile(p);
    const a = p.analysis || Demo.analysis(p);
    const names = (p.characters || []).map((c) => c.name);
    const hero = names[0] || g.hero;
    const baseTitle = (p.title && p.title !== 'Nouveau webtoon' && p.title !== 'Projet démo ToonIA') ? p.title : '';
    const titles = [baseTitle, 'Le Prix du Silence', 'Fissures', 'Ce que ' + hero + ' a oublié', g.places[0].name].filter(Boolean);
    const uniq = [...new Set(titles)].slice(0, 4);
    const premise = firstSentence(p.idea, g.conflict);
    return {
      titleProposals: uniq,
      synopsisVariants: [
        { lengthType: 'Court', text: premise, tagline: 'Tout finit par se fissurer.' },
        { lengthType: 'Moyen', text: premise + ' ' + g.conflict + ' Pour ' + hero + ', la seule issue honorable est aussi la plus dangereuse.', tagline: 'Le prix à payer était déjà écrit.' },
        { lengthType: 'Long', text: premise + '\n\n' + g.conflict + ' ' + g.lore + '\n\n' + hero + ' devra choisir entre la vérité qui détruit et le mensonge qui protège. ' + g.stakes, tagline: 'Certaines vérités coûtent plus cher que le silence.' }
      ],
      coverProposal: {
        composition: 'Plan rapproché en contre-plongée de ' + hero + ' au centre droit, occupant le tiers inférieur ; ' + g.places[0].name + ' en arrière-plan flou sur le tiers supérieur.',
        charactersPresent: [hero],
        atmosphereAndLighting: g.visual.lighting + '. Ambiance : ' + (a.ton || g.tone),
        colorPalette: g.visual.palette,
        detailedDescriptionNoText: 'Illustration de couverture de webtoon, ' + g.visual.artStyle + '. ' + hero + ' (' + g.look + ') de trois-quarts, regard vers le hors-champ. Décor : ' + g.places[0].desc + ' Composition verticale laissant une zone sombre en haut pour le titre. Palette : ' + g.visual.palette + '. Aucun texte, aucun logo, aucune bulle, aucune typographie dans l’image.'
      },
      selectedTitle: uniq[0] || 'Sans titre',
      selectedSynopsis: premise,
      selectedTagline: 'Tout finit par se fissurer.',
      selectedCoverDescription: '',
      isValidated: false
    };
  };

  /* ---------------- client Gemini ---------------- */
  const AI = (T.ai = {});

  AI.hasKey = () => !!(T.state && T.state.settings && T.state.settings.apiKey && String(T.state.settings.apiKey).trim());
  AI.server = { checked: false, hasKey: false, model: null };
  AI.hasServerKey = () => !!(AI.server && AI.server.hasKey);
  AI.isDemo = () =>
    !!(T.state && T.state.settings && T.state.settings.demo) || (!AI.hasKey() && !AI.hasServerKey());

  /**
   * Détecte le proxy /api/gemini du déploiement (clé en variable d'environnement).
   * Silencieux en cas d'absence : l'app retombe simplement en mode démo.
   */
  AI.checkServer = async function () {
    try {
      const res = await fetch('/api/config', { cache: 'no-store' });
      if (res.ok) {
        const cfg = await res.json();
        AI.server = { checked: true, hasKey: !!cfg.hasServerKey, model: cfg.model || null };
        if (cfg.model && !AI.hasKey()) T.state.settings.model = cfg.model;
      } else {
        AI.server = { checked: true, hasKey: false, model: null };
      }
    } catch (_) {
      AI.server = { checked: true, hasKey: false, model: null };
    }
    return AI.server;
  };

  function extractJson(data) {
    const cand = data && data.candidates && data.candidates[0];
    const text = cand && cand.content && cand.content.parts ? cand.content.parts.map((x) => x.text || '').join('') : '';
    if (!text) throw new Error('Réponse vide de Gemini (filtre de sécurité ou quota).');
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error('Réponse JSON invalide de Gemini.');
    }
  }

  async function callGemini(prompt, schema, temperature) {
    const key = (T.state.settings.apiKey || '').trim();
    const useServer = !key && AI.hasServerKey();
    if (!key && !useServer) {
      throw new Error('Aucune clé Gemini disponible : passez en mode démo, saisissez une clé locale, ou configurez GEMINI_API_KEY sur le déploiement.');
    }
    const model = T.state.settings.model || AI.server.model || 'gemini-3.1-flash-lite-preview';
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 90000);
    let res;

    try {
      if (useServer) {
        // La clé reste sur le serveur : on n'envoie que le prompt et le schéma.
        res = await fetch('/api/gemini', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: ctrl.signal,
          body: JSON.stringify({ prompt, schema, temperature: typeof temperature === 'number' ? temperature : 0.9, model })
        });
      } else {
        res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/' + encodeURIComponent(model) + ':generateContent?key=' + encodeURIComponent(key), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: ctrl.signal,
          body: JSON.stringify({
            contents: [{ role: 'user', parts: [{ text: prompt }] }],
            generationConfig: {
              temperature: typeof temperature === 'number' ? temperature : 0.9,
              responseMimeType: 'application/json',
              responseSchema: schema
            }
          })
        });
      }
    } catch (e) {
      clearTimeout(timer);
      if (e.name === 'AbortError') throw new Error('Délai dépassé (90 s) : Gemini n’a pas répondu.');
      throw new Error('Réseau indisponible ou requête bloquée. ' + (e.message || ''));
    }
    clearTimeout(timer);

    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const msg = data && data.error && data.error.message ? data.error.message : 'HTTP ' + res.status;
      const err = new Error((useServer ? 'Proxy : ' : 'Gemini : ') + msg);
      err.status = res.status;
      throw err;
    }
    return extractJson(data);
  }
  T.callGemini = callGemini;

  function canonContext(p) {
    const c = p.canon || {};
    const facts = (c.memories || []).map((m) => '- [' + m.category + '] ' + m.fact).join('\n') || '- (aucune mémoire canonique)';
    const chars = (c.characters || []).map((x) => '- ' + x.name + ' (' + x.role + ') : ' + (x.physicalAppearance || '') + ' | ' + (x.personality || '')).join('\n') || '- (aucun personnage validé)';
    const locs = (c.locations || []).map((l) => '- ' + l.name + ' : ' + l.description + ' | ' + (l.atmosphere || '')).join('\n') || '- (aucun lieu validé)';
    return 'CANON VALIDÉ (ne jamais contredire, ne jamais corriger en silence) :\nPersonnages :\n' + chars + '\nLieux :\n' + locs + '\nFaits :\n' + facts;
  }

  const S = {
    str: { type: 'STRING' },
    list: { type: 'ARRAY', items: { type: 'STRING' } }
  };

  const SCHEMAS = {
    analysis: {
      type: 'OBJECT',
      properties: {
        genrePrincipal: S.str, sousGenres: S.list, ton: S.str, themesDetectes: S.list,
        elementsNarratifsExplicites: S.list, ambiguitesAClarifier: S.list
      },
      required: ['genrePrincipal', 'sousGenres', 'ton', 'themesDetectes', 'elementsNarratifsExplicites', 'ambiguitesAClarifier']
    },
    characters: {
      type: 'OBJECT',
      properties: {
        characters: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              name: S.str, role: S.str, archetype: S.str, personality: S.str, physicalAppearance: S.str,
              distinctiveFeatures: S.str, characteristicClothing: S.str, abilities: S.str, relationships: S.str,
              plannedArc: S.str, currentState: S.str
            },
            required: ['name', 'role', 'archetype', 'personality', 'physicalAppearance', 'distinctiveFeatures', 'characteristicClothing', 'abilities', 'relationships', 'plannedArc', 'currentState']
          }
        }
      },
      required: ['characters']
    },
    world: {
      type: 'OBJECT',
      properties: {
        rules: S.str, factions: S.str, geography: S.str, socialStructure: S.str, historyLore: S.str,
        terminology: S.str, worldConstraints: S.str,
        locations: { type: 'ARRAY', items: { type: 'OBJECT', properties: { name: S.str, description: S.str, atmosphere: S.str }, required: ['name', 'description', 'atmosphere'] } },
        objects: { type: 'ARRAY', items: { type: 'OBJECT', properties: { name: S.str, significance: S.str, description: S.str }, required: ['name', 'significance', 'description'] } }
      },
      required: ['rules', 'factions', 'geography', 'socialStructure', 'historyLore', 'terminology', 'worldConstraints', 'locations', 'objects']
    },
    story: {
      type: 'OBJECT',
      properties: {
        premise: S.str, confirmedGenre: S.str, mainThemes: S.str, generalTone: S.str, mainConflicts: S.str,
        mainPlot: S.str, subPlots: S.str, narrativeDirection: S.str, pacing: S.str,
        timelineEvents: { type: 'ARRAY', items: { type: 'OBJECT', properties: { title: S.str, description: S.str, dateOrEpoch: S.str }, required: ['title', 'description', 'dateOrEpoch'] } }
      },
      required: ['premise', 'confirmedGenre', 'mainThemes', 'generalTone', 'mainConflicts', 'mainPlot', 'subPlots', 'narrativeDirection', 'pacing', 'timelineEvents']
    },
    visualHumor: {
      type: 'OBJECT',
      properties: {
        visual: { type: 'OBJECT', properties: { artStyle: S.str, colorPalette: S.str, panelingStyle: S.str, lightingMood: S.str, recurringVisualElements: S.str, framingRules: S.str, continuityElements: S.str }, required: ['artStyle', 'colorPalette', 'panelingStyle', 'lightingMood', 'recurringVisualElements', 'framingRules', 'continuityElements'] },
        humor: { type: 'OBJECT', properties: { humorStyle: S.str, chibiMomentsAllowed: { type: 'BOOLEAN' }, runningGags: S.str, punchlineFrequency: S.str, characterHumorHooks: S.str, comicSituations: S.str }, required: ['humorStyle', 'chibiMomentsAllowed', 'runningGags', 'punchlineFrequency', 'characterHumorHooks', 'comicSituations'] }
      },
      required: ['visual', 'humor']
    },
    chapter: {
      type: 'OBJECT',
      properties: {
        title: S.str, synopsis: S.str,
        scenes: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              title: S.str, locationName: S.str, charactersPresent: S.list, summary: S.str, beats: S.list,
              atmosphere: S.str, emotionalArc: S.str, pacingNotes: S.str
            },
            required: ['title', 'locationName', 'charactersPresent', 'summary', 'beats', 'atmosphere', 'emotionalArc', 'pacingNotes']
          }
        }
      },
      required: ['title', 'synopsis', 'scenes']
    },
    panels: {
      type: 'OBJECT',
      properties: {
        panels: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              charactersPresent: S.str, location: S.str, actionDescription: S.str, dominantEmotion: S.str,
              cameraAngle: S.str, composition: S.str, lightingAndAtmosphere: S.str, backgroundDetails: S.str,
              visualEffects: S.str, visualPrompt: S.str, continuityNotes: S.str, soundEffects: S.str
            },
            required: ['charactersPresent', 'location', 'actionDescription', 'dominantEmotion', 'cameraAngle', 'composition', 'lightingAndAtmosphere', 'backgroundDetails', 'visualEffects', 'visualPrompt', 'continuityNotes']
          }
        }
      },
      required: ['panels']
    },
    dialogues: {
      type: 'OBJECT',
      properties: {
        dialogues: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              speakerName: S.str, type: { type: 'STRING', enum: ['SPEECH', 'THOUGHT', 'SHOUT', 'WHISPER', 'NARRATION'] },
              emotion: S.str, placement: { type: 'STRING', enum: ['TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER'] },
              text: S.str, soundEffect: S.str
            },
            required: ['speakerName', 'type', 'emotion', 'placement', 'text']
          }
        }
      },
      required: ['dialogues']
    },
    marketing: {
      type: 'OBJECT',
      properties: {
        titleProposals: S.list,
        synopsisVariants: { type: 'ARRAY', items: { type: 'OBJECT', properties: { lengthType: S.str, text: S.str, tagline: S.str }, required: ['lengthType', 'text', 'tagline'] } },
        coverProposal: { type: 'OBJECT', properties: { composition: S.str, charactersPresent: S.list, atmosphereAndLighting: S.str, colorPalette: S.str, detailedDescriptionNoText: S.str }, required: ['composition', 'charactersPresent', 'atmosphereAndLighting', 'colorPalette', 'detailedDescriptionNoText'] }
      },
      required: ['titleProposals', 'synopsisVariants', 'coverProposal']
    }
  };

  function projectBrief(p) {
    const a = p.analysis || Demo.analysis(p);
    return 'PROJET : ' + p.title + '\nIDÉE : ' + p.idea + '\nGENRE : ' + a.genrePrincipal + '\nTON : ' + a.ton + '\nTHÈMES : ' + (a.themesDetectes || []).join(', ') + '\n' + canonContext(p);
  }

  AI.analyze = async function (p) {
    const base = Demo.analysis(p);
    if (AI.isDemo()) return base;
    const adv = p.advanced || {};
    const advTxt = Object.keys(adv).filter((k) => adv[k]).map((k) => k + '=' + adv[k]).join('\n') || '(aucun)';
    const prompt = 'Tu es analyste narratif pour un studio de webtoon. Analyse l’idée et renvoie un JSON strict.\n\nTITRE : ' + p.title + '\nIDÉE :\n' + p.idea + '\n\nDÉTAILS AVANCÉS :\n' + advTxt + '\n\nRÈGLES :\n- genrePrincipal : un genre principal court.\n- sousGenres : 2 à 4 sous-genres.\n- ton : description courte du ton narratif.\n- themesDetectes : 3 à 5 thèmes présents dans l’idée.\n- elementsNarratifsExplicites : ce que l’auteur a explicitement posé (repris fidèlement, sans invention).\n- ambiguitesAClarifier : 2 à 5 questions précises à poser à l’auteur. Ne pas inventer de réponse.\n- Ne corrige pas l’idée, ne complète pas les vides. Français.';
    const out = await callGemini(prompt, SCHEMAS.analysis, 0.6);
    return Object.assign(base, out, { _genreId: base._genreId });
  };

  AI.characters = async function (p) {
    const base = Demo.characters(p);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la Character Bible (3 à 5 personnages) en respectant strictement le canon validé. Chaque personnage doit être visuellement et psychologiquement distinct, et cohérent avec les noms déjà utilisés. Décris l’apparence physique avec les couleurs de cheveux et d’yeux (indispensable pour la continuité). Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.characters, 0.9);
    if (!out.characters || !out.characters.length) return base;
    return out.characters.map((c, i) => Object.assign({}, base[i % base.length], c, { id: T.uid('char'), isValidated: false }));
  };

  AI.world = async function (p) {
    const base = Demo.world(p);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la World Bible. Règles du monde contraignantes et applicables, factions, géographie, structure sociale, lore, terminologie (liste à puces), contraintes. Puis 3 lieux et 2 objets narratifs liés aux personnages validés. Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.world, 0.9);
    return Object.assign(base, out, {
      locations: (out.locations || base.locations).map((l) => Object.assign({ id: T.uid('loc'), isValidated: false }, l)),
      objects: (out.objects || base.objects).map((o) => Object.assign({ id: T.uid('obj'), ownerCharacterId: null, isValidated: false }, o)),
      isValidated: false
    });
  };

  AI.story = async function (p) {
    const base = Demo.story(p);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la Story Bible. Prémisse fidèle à l’idée, thèmes, ton, conflits (interne/relationnel/sociétal), intrigue principale en 3 actes, sous-intrigues, climax et promesse finale, rythme. Puis 3 événements fondateurs antérieurs au chapitre 1. Ne contredis aucun fait canonique. Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.story, 0.9);
    return Object.assign(base, out, {
      timelineEvents: (out.timelineEvents || base.timelineEvents).map((e, i) => Object.assign({ id: T.uid('evt'), orderIndex: i + 1, isValidated: false }, e)),
      isValidated: false
    });
  };

  AI.visualHumor = async function (p) {
    const base = { visual: Demo.visual(p), humor: Demo.humor(p) };
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : définis la direction artistique et l’ADN humoristique. Le style doit être compatible avec un webtoon à défilement vertical. L’humour doit rester cohérent avec le ton (si le récit est tragique, pas de burlesque gratuit). Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.visualHumor, 0.9);
    return { visual: Object.assign(base.visual, out.visual || {}, { isValidated: false }), humor: Object.assign(base.humor, out.humor || {}, { isValidated: false }) };
  };

  AI.chapter = async function (p, number) {
    const base = Demo.chapter(p, number);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : écris le script du chapitre ' + number + '. 3 à 5 scènes, chacune avec lieu canonique, personnages présents, résumé d’action et 3 à 5 beats narratifs. Pas encore de découpage en panels. Le chapitre doit faire avancer l’intrigue principale sans contredire le canon. Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.chapter, 0.95);
    return {
      id: T.uid('chap'), number, title: out.title || base.title, synopsis: out.synopsis || base.synopsis, isCanon: false,
      scenes: (out.scenes && out.scenes.length ? out.scenes : base.scenes).map((s, i) => ({
        id: T.uid('scene'), orderIndex: i + 1, title: s.title, locationName: s.locationName || '', charactersPresent: s.charactersPresent || [],
        summary: s.summary || '', beats: s.beats || [], atmosphere: s.atmosphere || '', emotionalArc: s.emotionalArc || '',
        pacingNotes: s.pacingNotes || '', detail: null, panels: [], isValidated: false
      }))
    };
  };

  AI.panels = async function (p, scene) {
    const base = Demo.panels(p, scene);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nSCÈNE À DÉCOUPER :\nTitre : ' + scene.title + '\nLieu : ' + scene.locationName + '\nPersonnages : ' + (scene.charactersPresent || []).join(', ') + '\nRésumé : ' + scene.summary + '\nBeats : ' + (scene.beats || []).join(' | ') + '\n\nTÂCHE : découpe cette scène en 3 à 6 panels de webtoon vertical. Pour chaque panel : personnages présents avec leur état, lieu, action, émotion dominante, angle de caméra, composition, éclairage/ambiance, arrière-plan, effets visuels, prompt visuel autonome (EN ANGLAIS, SANS TEXTE dans l’image), notes de continuité, onomatopée éventuelle. Français pour les descriptions. JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.panels, 0.95);
    if (!out.panels || !out.panels.length) return base;
    return out.panels.map((pn, i) => Object.assign({ id: T.uid('panel'), orderIndex: i + 1, artSeed: scene.id + ':' + i, isValidated: false, soundEffects: null }, pn));
  };

  AI.dialogues = async function (p, panel) {
    const base = Demo.dialogues(p, panel);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nPANEL :\nLieu : ' + panel.location + '\nPersonnages : ' + panel.charactersPresent + '\nAction : ' + panel.actionDescription + '\nÉmotion : ' + panel.dominantEmotion + '\n\nTÂCHE : écris 2 à 4 bulles pour ce panel. Types autorisés : SPEECH, THOUGHT, SHOUT, WHISPER, NARRATION. Placements : TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER. Textes courts (max 140 caractères), naturels, cohérents avec le canon. Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.dialogues, 0.95);
    if (!out.dialogues || !out.dialogues.length) return base;
    return out.dialogues.map((d) => Object.assign({ id: T.uid('dlg') }, d));
  };

  AI.marketing = async function (p) {
    const base = Demo.marketing(p);
    if (AI.isDemo()) return base;
    const prompt = projectBrief(p) + '\n\nTÂCHE : packaging éditorial. 3 à 5 propositions de titre (courtes, mémorables). 3 synopsis (Court 1-2 phrases, Moyen 1 paragraphe, Long 2 paragraphes) avec une tagline pour chacun. Une description de couverture détaillée SANS AUCUN TEXTE dans l’image (composition, personnages, ambiance, palette), le titre sera superposé par l’application. Ne contredis pas le canon. Français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.marketing, 1.0);
    return Object.assign(base, out, {
      selectedTitle: (out.titleProposals && out.titleProposals[0]) || base.selectedTitle,
      selectedSynopsis: (out.synopsisVariants && out.synopsisVariants[1] && out.synopsisVariants[1].text) || base.selectedSynopsis,
      selectedTagline: (out.synopsisVariants && out.synopsisVariants[0] && out.synopsisVariants[0].tagline) || base.selectedTagline,
      selectedCoverDescription: (out.coverProposal && out.coverProposal.detailedDescriptionNoText) || '',
      isValidated: false
    });
  };

  AI.testKey = async function () {
    const key = (T.state.settings.apiKey || '').trim();
    if (!key && AI.hasServerKey()) {
      const res = await fetch('/api/gemini', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: 'Réponds uniquement : OK', temperature: 0 })
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new Error((data && data.error && data.error.message) || 'HTTP ' + res.status);
      return true;
    }
    if (!key) throw new Error('Aucune clé à tester.');
    const model = T.state.settings.model || 'gemini-3.1-flash-lite-preview';
    const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/' + encodeURIComponent(model) + ':generateContent?key=' + encodeURIComponent(key), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'Réponds uniquement : OK' }] }], generationConfig: { maxOutputTokens: 5 } })
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error((data && data.error && data.error.message) || 'HTTP ' + res.status);
    return true;
  };

  /* ---------------- visuels procéduraux (SVG) ---------------- */
  const PALETTES = {
    cyberpunk: ['#6d4bd8', '#f0428b', '#22d3ee', '#08060f'],
    fantasy: ['#2f7d5a', '#e3c27a', '#1b3b5a', '#0d1220'],
    romance: ['#f6b7c9', '#e8d5b7', '#8ab6d6', '#3b2a3a'],
    horreur: ['#8a1c2b', '#4d6b52', '#cfc7b8', '#0a0a0c'],
    action: ['#f97316', '#fbbf24', '#1e3a8a', '#0b0a12'],
    mystere: ['#2c4a63', '#d9b26a', '#8a95a0', '#0a0d12'],
    default: ['#6d4bd8', '#f0428b', '#22d3ee', '#0b0912']
  };

  function paletteFor(seed, genreId) {
    const base = PALETTES[genreId] || PALETTES.default;
    const rng = T.rngFor(seed + 'pal');
    return rng() > 0.62 ? base.slice().reverse() : base;
  }

  T.art = function (seed, genreId) {
    const rng = T.rngFor(seed || 'art');
    const [c1, c2, c3, ink] = paletteFor(seed, genreId);
    const horizon = 55 + Math.floor(rng() * 20);
    const sunX = 15 + Math.floor(rng() * 70);
    const sunY = 18 + Math.floor(rng() * 26);
    const sunR = 12 + Math.floor(rng() * 14);
    const skyline = [];
    let x = -5;
    while (x < 105) {
      const w = 4 + rng() * 10;
      const h = 8 + rng() * 34;
      skyline.push('<rect x="' + x.toFixed(1) + '" y="' + (horizon + 20 - h).toFixed(1) + '" width="' + w.toFixed(1) + '" height="' + (h + 22).toFixed(1) + '" fill="' + ink + '" opacity="' + (0.55 + rng() * 0.4).toFixed(2) + '"/>');
      x += w + 1 + rng() * 3;
    }
    const rays = Array.from({ length: 14 }, (_, i) => {
      const a = (i / 14) * Math.PI * 2;
      return '<line x1="' + (sunX + Math.cos(a) * (sunR + 2)).toFixed(1) + '" y1="' + (sunY + Math.sin(a) * (sunR + 2)).toFixed(1) + '" x2="' + (sunX + Math.cos(a) * 90).toFixed(1) + '" y2="' + (sunY + Math.sin(a) * 90).toFixed(1) + '" stroke="' + c3 + '" stroke-width="0.5" opacity="0.18"/>';
    }).join('');
    const figure = rng() > 0.35
      ? '<g transform="translate(' + (62 + rng() * 20).toFixed(1) + ',' + (horizon + 2).toFixed(1) + ') scale(' + (0.85 + rng() * 0.5).toFixed(2) + ')">' +
        '<ellipse cx="0" cy="0" rx="5.4" ry="1.2" fill="#000" opacity="0.55"/>' +
        '<path d="M-3.4 -1 L-2.6 -14 Q0 -16 2.6 -14 L3.4 -1 Z" fill="' + ink + '"/>' +
        '<circle cx="0" cy="-17.4" r="3.1" fill="' + ink + '"/>' +
        '<path d="M-2.4 -17 Q0 -20.4 2.4 -17 Q2.8 -14.6 0 -13.8 Q-2.8 -14.6 -2.4 -17" fill="' + c1 + '" opacity="0.9"/>' +
        '</g>'
      : '';
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 100" preserveAspectRatio="xMidYMid slice">' +
      '<defs>' +
      '<linearGradient id="sky" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="' + c1 + '"/><stop offset="0.55" stop-color="' + c2 + '" stop-opacity="0.75"/><stop offset="1" stop-color="' + ink + '"/></linearGradient>' +
      '<radialGradient id="glow"><stop offset="0" stop-color="' + c3 + '" stop-opacity="0.95"/><stop offset="1" stop-color="' + c3 + '" stop-opacity="0"/></radialGradient>' +
      '<pattern id="dots" width="4" height="4" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="0.75" fill="#fff" opacity="0.16"/></pattern>' +
      '</defs>' +
      '<rect width="160" height="100" fill="url(#sky)"/>' +
      rays +
      '<circle cx="' + sunX + '" cy="' + sunY + '" r="' + (sunR * 2.6) + '" fill="url(#glow)"/>' +
      '<circle cx="' + sunX + '" cy="' + sunY + '" r="' + sunR + '" fill="' + c3 + '" opacity="0.85"/>' +
      '<rect y="' + horizon + '" width="160" height="' + (100 - horizon) + '" fill="' + ink + '" opacity="0.9"/>' +
      skyline.join('') +
      '<rect width="160" height="100" fill="url(#dots)"/>' +
      figure +
      '<rect x="0.6" y="0.6" width="158.8" height="98.8" fill="none" stroke="#000" stroke-width="1.2" opacity="0.55"/>' +
      '<rect width="160" height="100" fill="' + ink + '" opacity="0.06"/>' +
      '</svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  };

  /* ---------------- moteur canonique ---------------- */
  const Canon = (T.canon = {});

  const HAIR = ['blond', 'brun', 'noir', 'roux', 'blanc', 'argenté', 'bleu', 'vert', 'rose'];
  const EYES = ['bleu', 'vert', 'noir', 'marron', 'rouge', 'doré', 'ambre', 'violet'];

  function low(s) { return String(s || '').toLowerCase(); }

  function visualDivergence(canonDesc, newDesc) {
    const cd = low(canonDesc), nd = low(newDesc);
    if (!cd || !nd) return null;
    for (const color of HAIR) {
      if (cd.includes('cheveux ' + color) || cd.includes('chevelure ' + color)) {
        for (const other of HAIR) {
          if (other !== color && (nd.includes('cheveux ' + other) || nd.includes('chevelure ' + other))) {
            return 'Incompatibilité capillaire : cheveux ' + color + ' dans le canon, mais décrits avec des cheveux ' + other + ' dans le nouvel élément.';
          }
        }
      }
    }
    for (const color of EYES) {
      if (cd.includes('yeux ' + color)) {
        for (const other of EYES) {
          if (other !== color && nd.includes('yeux ' + other)) {
            return 'Incompatibilité oculaire : yeux ' + color + ' dans le canon, mais décrits avec des yeux ' + other + ' dans le nouvel élément.';
          }
        }
      }
    }
    return null;
  }
  Canon.visualDivergence = visualDivergence;

  function C(targetType, targetName, fieldName, canonicalValue, conflictingValue, description, severity) {
    return { id: T.uid('crt'), targetType, targetName, fieldName, canonicalValue, conflictingValue, description, severity };
  }

  Canon.detectCharacter = function (char, p) {
    const out = [];
    const c = p.canon || {};
    const existing = (c.characters || []).find((x) => low(x.name).trim() === low(char.name).trim() || x.id === char.id);
    if (existing) {
      if (existing.role && char.role && low(existing.role) !== low(char.role)) {
        out.push(C('Personnage', existing.name, 'Rôle', existing.role, char.role, 'Le rôle de ' + existing.name + ' dans le canon est « ' + existing.role + ' », mais le nouvel élément le définit comme « ' + char.role + ' ».', 'HIGH'));
      }
      if (existing.archetype && char.archetype && low(existing.archetype) !== low(char.archetype)) {
        out.push(C('Personnage', existing.name, 'Archétype', existing.archetype, char.archetype, 'L’archétype établi est « ' + existing.archetype + ' », différent de « ' + char.archetype + ' ».', 'MEDIUM'));
      }
      const vd = visualDivergence(existing.physicalAppearance, char.physicalAppearance);
      if (vd) out.push(C('Personnage', existing.name, 'Apparence visuelle', existing.physicalAppearance, char.physicalAppearance, vd, 'HIGH'));
    }
    const death = (c.memories || []).find((m) => low(m.fact).includes(low(char.name)) && /\b(mort|morte|meurt|mourir|décédé|décédée|décès|tué|tuée)\b/.test(low(m.fact)));
    if (death && !low(char.personality).includes('résurrection') && !low(char.plannedArc).includes('résurrection')) {
      out.push(C('Personnage', char.name, 'Statut vital (canon)', death.fact, 'Personnage réintroduit actif', 'Une mémoire canonique établit que « ' + death.fact + ' ». La réintroduction de ' + char.name + ' contredit ce fait absolu.', 'HIGH'));
    }
    return out;
  };

  Canon.detectLocation = function (loc, p) {
    const out = [];
    const existing = ((p.canon || {}).locations || []).find((x) => low(x.name).trim() === low(loc.name).trim());
    if (existing && existing.atmosphere && loc.atmosphere && low(existing.atmosphere) !== low(loc.atmosphere)) {
      out.push(C('Lieu', existing.name, 'Atmosphère', existing.atmosphere, loc.atmosphere, 'L’atmosphère canonique de ' + existing.name + ' est « ' + existing.atmosphere + ' », modifiée en « ' + loc.atmosphere + ' ».', 'MEDIUM'));
    }
    return out;
  };

  Canon.detectChapter = function (chapter, p) {
    const dup = ((p.canon || {}).chapters || []).find((x) => Number(x.number) === Number(chapter.number) && x.id !== chapter.id);
    if (dup) {
      return [C('Chapitre', 'Chapitre ' + chapter.number, 'Numéro', 'Chapitre existant : « ' + dup.title + ' »', 'Nouveau chapitre : « ' + chapter.title + ' »', 'Le numéro de chapitre ' + chapter.number + ' est déjà attribué au chapitre canonique « ' + dup.title + ' ».', 'HIGH')];
    }
    return [];
  };

  Canon.detectMemory = function (memory, p) {
    const existing = ((p.canon || {}).memories || []).find((m) => low(m.category) === low(memory.category) && low(m.fact).includes(low(String(memory.fact).slice(0, 20))) && m.id !== memory.id);
    if (existing && existing.fact !== memory.fact) {
      return [C('Mémoire canonique', memory.category, 'Fait établi', existing.fact, memory.fact, 'Un fait canonique similaire existe déjà avec une formulation différente.', 'MEDIUM')];
    }
    return [];
  };

  Canon.detectCover = function (cover, p) {
    const out = [];
    const desc = low(cover.visualDescription);
    const banned = ['avec le titre', 'titre écrit', 'texte écrit', 'logo incrusté', 'lettres dorées', 'typographie sur l’image', 'typographie sur l\'image', 'texte intégré', 'titre en gros'];
    for (const kw of banned) {
      if (desc.includes(kw)) {
        out.push(C('Couverture', cover.title || 'Couverture', 'Interdiction de texte intégré', 'Génération sans aucun texte (le texte est superposé nativement)', kw, 'La description de couverture demande « ' + kw + ' ». Règle absolue ToonIA : les illustrations sont générées SANS texte ni logo. Tout texte est rendu nativement par l’application.', 'HIGH'));
        break;
      }
    }
    for (const char of ((p.canon || {}).characters || [])) {
      if (desc.includes(low(char.name))) {
        const vd = visualDivergence(char.physicalAppearance, cover.visualDescription);
        if (vd) out.push(C('Couverture', char.name, 'Continuité visuelle du personnage', char.physicalAppearance, cover.visualDescription, 'Dans la couverture : ' + vd, 'MEDIUM'));
      }
    }
    return out;
  };

  Canon.detectSynopsis = function (title, synopsis, p) {
    const out = [];
    if (!synopsis) return out;
    const syn = low(synopsis);
    const sb = (p.canon || {}).storyBible;
    if (sb) {
      const grim = low(sb.mainThemes).includes('sombre') || low(sb.mainThemes).includes('tragique') || low(sb.narrativeDirection).includes('tragique');
      const comedy = syn.includes('comédie insouciante') || syn.includes('gags burlesques') || syn.includes('histoire légère et sans danger');
      if (grim && comedy) {
        out.push(C('Synopsis', title || 'Synopsis final', 'Harmonie tonale', 'Story Bible sombre/tragique : ' + sb.mainThemes, synopsis, 'Le synopsis présente une intrigue purement comique ou légère en contradiction directe avec les enjeux tragiques validés dans la Story Bible.', 'HIGH'));
      }
    }
    for (const char of ((p.canon || {}).characters || [])) {
      if (!syn.includes(low(char.name))) continue;
      const dead = low(char.personality).includes('mort') || low(char.personality).includes('décédé');
      if (dead && (syn.includes(low(char.name) + ' part') || syn.includes(low(char.name) + ' explore') || syn.includes(low(char.name) + ' combat aux côtés'))) {
        out.push(C('Synopsis', char.name, 'Statut vital', char.name + ' est canoniquement décédé', 'Décrit en action dans le synopsis', 'Le personnage ' + char.name + ' est validé comme décédé dans le canon, mais le synopsis le présente comme un protagoniste actif.', 'HIGH'));
      }
      if (low(char.role) === 'protagoniste' && (syn.includes('antagoniste ' + low(char.name)) || syn.includes('le traître ' + low(char.name)) || syn.includes(low(char.name) + ', l’ennemi juré'))) {
        out.push(C('Synopsis', char.name, 'Rôle canonique', 'Protagoniste majeur (' + char.name + ')', 'Décrit comme antagoniste/traître', char.name + ' est le protagoniste du canon, mais le synopsis le qualifie d’antagoniste.', 'HIGH'));
      }
    }
    return out;
  };

  Canon.detect = function (kind, item, p) {
    if (kind === 'character') return Canon.detectCharacter(item, p);
    if (kind === 'location') return Canon.detectLocation(item, p);
    if (kind === 'chapter') return Canon.detectChapter(item, p);
    if (kind === 'memory') return Canon.detectMemory(item, p);
    if (kind === 'cover') return Canon.detectCover(item, p);
    return [];
  };

  Canon.counts = function (p) {
    const c = (p && p.canon) || {};
    return {
      characters: (c.characters || []).length,
      locations: (c.locations || []).length,
      chapters: (c.chapters || []).length,
      memories: (c.memories || []).length
    };
  };

  T.stats = function (p) {
    const panels = ((p.chapters || []).flatMap((ch) => ch.scenes || []).flatMap((s) => s.panels || []));
    return {
      characters: (p.characters || []).length,
      locations: ((p.world && p.world.locations) || []).length,
      chapters: (p.chapters || []).length,
      scenes: (p.chapters || []).flatMap((ch) => ch.scenes || []).length,
      panels: panels.length,
      dialogues: panels.flatMap((pn) => pn.dialogues || []).length
    };
  };

  T.progress = function (p) {
    return [
      { key: 'projet', label: 'Idée', done: !!p.analysis },
      { key: 'personnages', label: 'Personnages', done: !!(p.characters && p.characters.length) },
      { key: 'univers', label: 'Univers', done: !!p.world },
      { key: 'histoire', label: 'Histoire', done: !!p.story },
      { key: 'direction', label: 'Direction', done: !!(p.visual && p.humor) },
      { key: 'chapitre', label: 'Chapitres', done: !!(p.chapters && p.chapters.length) },
      { key: 'panels', label: 'Panels', done: !!(p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).length)) },
      { key: 'dialogues', label: 'Dialogues', done: !!(p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).some((pn) => (pn.dialogues || []).length))) },
      { key: 'couverture', label: 'Couverture', done: !!p.marketing }
    ];
  };
})();
