import path from 'node:path';

export const ECOMMERCE_CONTRACT_FIELD_MAP = [
  {
    token: 'merchantNumber',
    contractField: 'N° commerçant',
    required: false,
    sources: [
      'dossierAffiliation.meta.numeroCommercant',
      'dossierAffiliation.commercant.numeroCommercant',
      'dossierAffiliation.commercant.idCommercant',
      'dossierAffiliation.reference',
      'reference'
    ]
  },
  {
    token: 'pointOfSaleReference',
    contractField: 'N° Point de Vente',
    required: false,
    sources: [
      'dossierAffiliation.meta.numeroPointDeVente',
      'dossierAffiliation.commercant.pointOfSaleReference',
      'dossierAffiliation.commercant.chainePointVente',
      'dossierAffiliation.commercant.nombrePointsVente'
    ]
  },
  {
    token: 'mcc',
    contractField: 'MCC',
    required: false,
    sources: ['dossierAffiliation.commercant.mcc', 'dossierAffiliation.mcc', 'mcc']
  },
  {
    token: 'raisonSociale',
    contractField: 'Raison sociale',
    required: true,
    sources: [
      'dossierAffiliation.commercant.raisonSociale',
      'dossierAffiliation.commercant.nomCommercial',
      'dossierAffiliation.commercant.nomEntite',
      'dossierAffiliation.raisonSociale'
    ]
  },
  {
    token: 'formeJuridique',
    contractField: 'Forme juridique',
    required: false,
    sources: [
      'dossierAffiliation.commercant.formeJuridique',
      'dossierAffiliation.formeJuridique'
    ]
  },
  {
    token: 'secteurActivite',
    contractField: "Secteur d'activité",
    required: false,
    sources: [
      'dossierAffiliation.commercant.secteur',
      'dossierAffiliation.commercant.activite',
      'dossierAffiliation.secteur',
      'dossierAffiliation.activite'
    ]
  },
  {
    token: 'enseigne',
    contractField: 'Enseigne / Acronyme',
    required: false,
    sources: [
      'dossierAffiliation.commercant.enseigne',
      'dossierAffiliation.commercant.nomCommercial',
      'dossierAffiliation.enseigne'
    ]
  },
  {
    token: 'adresse',
    contractField: 'Adresse',
    required: true,
    sources: ['dossierAffiliation.commercant.adresse', 'dossierAffiliation.adresse']
  },
  {
    token: 'quartier',
    contractField: 'Quartier',
    required: false,
    sources: [
      'dossierAffiliation.commercant.quartier',
      'dossierAffiliation.commercant.adresseComplement',
      'dossierAffiliation.quartier'
    ]
  },
  {
    token: 'codePostal',
    contractField: 'Code Postal',
    required: false,
    sources: [
      'dossierAffiliation.commercant.codePostal',
      'dossierAffiliation.codePostal'
    ]
  },
  {
    token: 'ville',
    contractField: 'Ville',
    required: true,
    sources: ['dossierAffiliation.commercant.ville', 'dossierAffiliation.ville']
  },
  {
    token: 'ice',
    contractField: 'ICE',
    required: false,
    sources: [
      'dossierAffiliation.commercant.ice',
      'dossierAffiliation.commercant.identifiantFiscal',
      'dossierAffiliation.ice'
    ]
  },
  {
    token: 'rc',
    contractField: 'Registre de Commerce',
    required: false,
    sources: [
      'dossierAffiliation.commercant.rc',
      'dossierAffiliation.commercant.registreCommerce',
      'dossierAffiliation.rc'
    ]
  },
  {
    token: 'patente',
    contractField: 'Taxe Professionnelle (Patente)',
    required: false,
    sources: [
      'dossierAffiliation.commercant.patente',
      'dossierAffiliation.commercant.taxeProfessionnelle',
      'dossierAffiliation.patente'
    ]
  },
  {
    token: 'representantLegal',
    contractField: 'Nom du représentant légal',
    required: false,
    sources: [
      'dossierAffiliation.commercant.representantLegal',
      'dossierAffiliation.representantLegal'
    ]
  },
  {
    token: 'fonction',
    contractField: 'Fonction',
    required: false,
    sources: [
      'dossierAffiliation.commercant.fonction',
      'dossierAffiliation.commercant.qualiteSignataire',
      'dossierAffiliation.fonction'
    ]
  },
  {
    token: 'telephoneFixe',
    contractField: 'Téléphone Fixe',
    required: false,
    sources: [
      'dossierAffiliation.commercant.telephoneFixe',
      'dossierAffiliation.commercant.telephoneSecondaire',
      'dossierAffiliation.telephoneFixe',
      'dossierAffiliation.telephoneSecondaire'
    ]
  },
  {
    token: 'gsm',
    contractField: 'GSM',
    required: true,
    sources: [
      'dossierAffiliation.commercant.telephone',
      'dossierAffiliation.commercant.telephonePrincipal',
      'dossierAffiliation.telephone',
      'dossierAffiliation.telephonePrincipal',
      'dossierAffiliation.commercant.telephoneSecondaire'
    ]
  },
  {
    token: 'emailReleve',
    contractField: "E-mail d'envoi du relevé",
    required: true,
    sources: [
      'dossierAffiliation.commercant.emailReleve',
      'dossierAffiliation.commercant.email',
      'dossierAffiliation.commercant.emailContact',
      'dossierAffiliation.email',
      'dossierAffiliation.emailContact'
    ]
  },
  {
    token: 'beneficiairesEffectifs',
    contractField: 'Nom(s) Bénéficiaire(s) Effectif(s)',
    required: false,
    sources: [
      'dossierAffiliation.commercant.beneficiairesEffectifs',
      'dossierAffiliation.beneficiairesEffectifs'
    ]
  },
  {
    token: 'cin',
    contractField: 'CIN',
    required: false,
    sources: [
      'dossierAffiliation.commercant.cin',
      'dossierAffiliation.cin'
    ]
  },
  {
    token: 'dateNaissance',
    contractField: 'Date de naissance',
    required: false,
    sources: [
      'dossierAffiliation.commercant.dateNaissance',
      'dossierAffiliation.dateNaissance'
    ]
  },
  {
    token: 'nationalite',
    contractField: 'Nationalité',
    required: false,
    sources: [
      'dossierAffiliation.commercant.nationalite',
      'dossierAffiliation.nationalite'
    ]
  },
  {
    token: 'siteMarchandUrl',
    contractField: 'URL du site marchand',
    required: false,
    sources: [
      'dossierAffiliation.services.urlSite',
      'dossierAffiliation.services.siteMarchandUrl',
      'dossierAffiliation.siteMarchandUrl'
    ]
  },
  {
    token: 'applicationMobile',
    contractField: "Nom de l'application mobile",
    required: false,
    sources: [
      'dossierAffiliation.services.nomApp',
      'dossierAffiliation.services.applicationMobile',
      'dossierAffiliation.applicationMobile'
    ]
  },
  {
    token: 'commissionLocaleEcommerce',
    contractField: 'Taux commission locale',
    required: false,
    sources: [
      'dossierAffiliation.tarification.commissionLocale',
      'dossierAffiliation.tarification.commissionLocaleEcommerce',
      'dossierAffiliation.commissionLocaleEcommerce',
      'dossierAffiliation.commissionLocale'
    ]
  },
  {
    token: 'commissionEtrangereEcommerce',
    contractField: 'Taux commission étrangère',
    required: false,
    sources: [
      'dossierAffiliation.tarification.commissionEtrangere',
      'dossierAffiliation.tarification.commissionEtrangereEcommerce',
      'dossierAffiliation.commissionEtrangereEcommerce',
      'dossierAffiliation.commissionEtrangere'
    ]
  },
  {
    token: 'fraisMiseEnServiceEcommerce',
    contractField: 'Frais de mise en service',
    required: false,
    sources: [
      'dossierAffiliation.tarification.fraisMiseEnService',
      'dossierAffiliation.tarification.fraisMiseEnServiceEcommerce',
      'dossierAffiliation.fraisMiseEnServiceEcommerce'
    ]
  }
];

export function buildEcommerceContractArtifacts(input) {
  const data = normalizeInputEnvelope(input);
  const mappingReport = [];
  const textValues = {};

  for (const definition of ECOMMERCE_CONTRACT_FIELD_MAP) {
    const resolved = resolveFromPaths(data, definition.sources);
    const plainValue = normalizeDisplayValue(resolved.value);
    textValues[definition.token] = toTemplateText(plainValue);
    mappingReport.push({
      token: definition.token,
      contractField: definition.contractField,
      required: definition.required,
      source: resolved.source,
      value: plainValue
    });
  }

  const serviceModes = resolveServiceModes(data);
  const ribParts = resolveBankAccountParts(data);
  const contractDate = resolveContractDate(data);

  const templateValues = {
    ...textValues,
    creationCheck: toCheckboxHtml(true),
    avenantCheck: toCheckboxHtml(false),
    integrationSiteCheck: toCheckboxHtml(serviceModes.siteMarchand),
    integrationMobileCheck: toCheckboxHtml(serviceModes.applicationMobile),
    payByLinkManuelCheck: toCheckboxHtml(serviceModes.payByLinkManuel),
    payByLinkAutomatiqueCheck: toCheckboxHtml(serviceModes.payByLinkAutomatique),
    bankCodeCells: toRibCellsHtml(ribParts.bankCode, 3),
    cityCodeCells: toRibCellsHtml(ribParts.cityCode, 3),
    accountNumberCells: toRibCellsHtml(ribParts.accountNumber, 16),
    ribKeyCells: toRibCellsHtml(ribParts.ribKey, 2),
    currencyCells: toRibCellsHtml(ribParts.currency, 3),
    faitAVille: textValues.ville,
    contractDate: toTemplateText(contractDate)
  };

  const missingRequiredFields = mappingReport
    .filter((entry) => entry.required && !entry.value)
    .map((entry) => entry.contractField);

  if (!templateValues.siteMarchandUrl && serviceModes.siteMarchand) {
    missingRequiredFields.push('URL du site marchand');
  }

  if (
    !serviceModes.siteMarchand
    && !serviceModes.applicationMobile
    && !serviceModes.payByLinkManuel
    && !serviceModes.payByLinkAutomatique
  ) {
    missingRequiredFields.push('Au moins un service e-commerce');
  }

  return {
    templateValues,
    mappingReport,
    warnings: {
      missingRequiredFields: Array.from(new Set(missingRequiredFields))
    }
  };
}

export function renderTemplate(templateHtml, templateValues) {
  return Object.entries(templateValues).reduce(
    (html, [token, value]) => html.replaceAll(`{{${token}}}`, value ?? ''),
    templateHtml
  );
}

function normalizeInputEnvelope(input) {
  if (input && typeof input === 'object' && input.dossierAffiliation) {
    return input;
  }

  return {
    dossierAffiliation: input ?? {}
  };
}

function resolveFromPaths(root, paths) {
  for (const sourcePath of paths) {
    const candidate = getByPath(root, sourcePath);
    if (hasMeaningfulValue(candidate)) {
      return {
        source: sourcePath,
        value: candidate
      };
    }
  }

  return {
    source: '',
    value: ''
  };
}

function getByPath(object, dottedPath) {
  return dottedPath.split('.').reduce((current, key) => {
    if (current === null || current === undefined) {
      return undefined;
    }

    return current[key];
  }, object);
}

function resolveServiceModes(root) {
  const modeValue = normalizeToken(
    resolveFromPaths(root, [
      'dossierAffiliation.services.modeServiceEcommerce',
      'dossierAffiliation.modeServiceEcommerce'
    ]).value
  );

  const sourceFlags = {
    siteMarchand: resolveBoolean(root, [
      'dossierAffiliation.services.integrationSiteMarchand',
      'dossierAffiliation.services.siteMarchand',
      'dossierAffiliation.services.integrationSite'
    ]),
    applicationMobile: resolveBoolean(root, [
      'dossierAffiliation.services.integrationApplicationMobile',
      'dossierAffiliation.services.applicationMobileActive',
      'dossierAffiliation.services.applicationMobile'
    ]),
    payByLinkManuel: resolveBoolean(root, [
      'dossierAffiliation.services.payByLinkManuel',
      'dossierAffiliation.services.payByLinkManual'
    ]),
    payByLinkAutomatique: resolveBoolean(root, [
      'dossierAffiliation.services.payByLinkAutomatique',
      'dossierAffiliation.services.payByLinkAutomatic'
    ])
  };

  return {
    siteMarchand: sourceFlags.siteMarchand || modeValue === 'sitemarchand',
    applicationMobile: sourceFlags.applicationMobile || modeValue === 'applicationmobile',
    payByLinkManuel: sourceFlags.payByLinkManuel || modeValue === 'paybylinkmanuel',
    payByLinkAutomatique:
      sourceFlags.payByLinkAutomatique || modeValue === 'paybylinkautomatique'
  };
}

function resolveBoolean(root, paths) {
  for (const sourcePath of paths) {
    const value = getByPath(root, sourcePath);
    if (value === true) {
      return true;
    }
  }

  return false;
}

function resolveBankAccountParts(root) {
  const account = {
    bankCode: normalizeDisplayValue(
      resolveFromPaths(root, [
        'dossierAffiliation.compteBancaire.codeBanque',
        'dossierAffiliation.codeBanque'
      ]).value
    ),
    cityCode: normalizeDisplayValue(
      resolveFromPaths(root, [
        'dossierAffiliation.compteBancaire.codeVille',
        'dossierAffiliation.codeVille'
      ]).value
    ),
    accountNumber: normalizeDisplayValue(
      resolveFromPaths(root, [
        'dossierAffiliation.compteBancaire.numeroCompte',
        'dossierAffiliation.numeroCompte'
      ]).value
    ),
    ribKey: normalizeDisplayValue(
      resolveFromPaths(root, [
        'dossierAffiliation.compteBancaire.cleRib',
        'dossierAffiliation.cleRib'
      ]).value
    ),
    currency: normalizeDisplayValue(
      resolveFromPaths(root, [
        'dossierAffiliation.compteBancaire.devise',
        'dossierAffiliation.devise'
      ]).value
    )
  };

  const rawRib = normalizeDisplayValue(
    resolveFromPaths(root, [
      'dossierAffiliation.compteBancaire.rib',
      'dossierAffiliation.rib'
    ]).value
  ).replaceAll(/[^A-Za-z0-9]/g, '');

  if (!account.bankCode) {
    account.bankCode = rawRib.slice(0, 3);
  }
  if (!account.cityCode) {
    account.cityCode = rawRib.slice(3, 6);
  }
  if (!account.accountNumber) {
    account.accountNumber = rawRib.slice(6, 22);
  }
  if (!account.ribKey) {
    account.ribKey = rawRib.slice(22, 24);
  }
  if (!account.currency) {
    account.currency = rawRib.slice(24, 27);
  }

  return account;
}

function resolveContractDate(root) {
  const explicitValue = normalizeDisplayValue(
    resolveFromPaths(root, [
      'dossierAffiliation.meta.contractDate',
      'dossierAffiliation.generatedAt',
      'dossierAffiliation.dateLabel',
      'dossierAffiliation.dateSoumission'
    ]).value
  );

  if (explicitValue) {
    return explicitValue;
  }

  const today = new Date();
  const day = String(today.getDate()).padStart(2, '0');
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const year = String(today.getFullYear());
  return `${day}/${month}/${year}`;
}

function normalizeDisplayValue(value) {
  if (Array.isArray(value)) {
    return value
      .map((entry) => normalizeDisplayValue(entry))
      .filter(Boolean)
      .join(', ');
  }

  if (value === null || value === undefined) {
    return '';
  }

  return String(value).trim();
}

function hasMeaningfulValue(value) {
  if (Array.isArray(value)) {
    return value.length > 0;
  }

  if (value === null || value === undefined) {
    return false;
  }

  if (typeof value === 'boolean') {
    return value;
  }

  return String(value).trim().length > 0;
}

function toTemplateText(value) {
  return value ? escapeHtml(value) : '&#160;';
}

function toCheckboxHtml(checked) {
  return checked ? '&#10003;' : '&#160;';
}

function toRibCellsHtml(value, count) {
  const compactValue = normalizeDisplayValue(value).replaceAll(/[^A-Za-z0-9]/g, '').toUpperCase();
  let html = '';

  for (let index = 0; index < count; index += 1) {
    const cellValue = index < compactValue.length ? escapeHtml(compactValue[index]) : '&#160;';
    html += `<div class="rib-c">${cellValue}</div>`;
  }

  return html;
}

function normalizeToken(value) {
  return normalizeDisplayValue(value).replaceAll(/[^A-Za-z0-9]/g, '').toLowerCase();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function defaultTemplatePath(fromFileUrl) {
  return path.resolve(path.dirname(fromFileUrl), '../../src/main/resources/contrats/contrat_affiliation_ecommerce.html');
}
