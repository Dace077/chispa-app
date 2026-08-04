#!/usr/bin/env node
/**
 * Generador de QR en PNG, sin dependencias.
 *
 * Usa el mismo codificador que GENERAR-QR.html, verificado contra los vectores
 * de prueba de la norma ISO/IEC 18004. Escribe el PNG a mano con el zlib que
 * ya trae Node: cero paquetes de npm, cero red.
 *
 *   node qr.js "https://ejemplo.com/app.apk" salida.png [--ecl M] [--scale 10]
 *   node qr.js --test        ejecuta la bateria de verificacion
 */

'use strict';
const fs = require('fs');
const zlib = require('zlib');

/* ========================= GF(256) y Reed-Solomon ========================= */
const EXP = new Uint8Array(512), LOG = new Uint8Array(256);
(function initGF() {
  let x = 1;
  for (let i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if (x & 0x100) x ^= 0x11D; }
  for (let i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
})();
const gmul = (a, b) => (a === 0 || b === 0) ? 0 : EXP[LOG[a] + LOG[b]];

function rsGenerator(deg) {
  let poly = [1];
  for (let i = 0; i < deg; i++) {
    const next = new Array(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gmul(poly[j], EXP[i]);
    }
    poly = next;
  }
  return poly;
}

function rsEncode(data, ecLen) {
  const gen = rsGenerator(ecLen);
  const buf = new Uint8Array(data.length + ecLen);
  buf.set(data);
  for (let i = 0; i < data.length; i++) {
    const coef = buf[i];
    if (coef !== 0) for (let j = 0; j < gen.length; j++) buf[i + j] ^= gmul(gen[j], coef);
  }
  return buf.slice(data.length);
}

/* ===================== Tablas del estandar (v1..v20) ===================== */
const EC_TABLE = {
  L: [[7,1,19,0,0],[10,1,34,0,0],[15,1,55,0,0],[20,1,80,0,0],[26,1,108,0,0],[18,2,68,0,0],[20,2,78,0,0],[24,2,97,0,0],[30,2,116,0,0],[18,2,68,2,69],[20,4,81,0,0],[24,2,92,2,93],[26,4,107,0,0],[30,3,115,1,116],[22,5,87,1,88],[24,5,98,1,99],[28,1,107,5,108],[30,5,120,1,121],[28,3,113,4,114],[28,3,107,5,108]],
  M: [[10,1,16,0,0],[16,1,28,0,0],[26,1,44,0,0],[18,2,32,0,0],[24,2,43,0,0],[16,4,27,0,0],[18,4,31,0,0],[22,2,38,2,39],[22,3,36,2,37],[26,4,43,1,44],[30,1,50,4,51],[22,6,36,2,37],[22,8,37,1,38],[24,4,40,5,41],[24,5,41,5,42],[28,7,45,3,46],[28,10,46,1,47],[26,9,43,4,44],[26,3,44,11,45],[26,3,41,13,42]],
  Q: [[13,1,13,0,0],[22,1,22,0,0],[18,2,17,0,0],[26,2,24,0,0],[18,2,15,2,16],[24,4,19,0,0],[18,2,14,4,15],[22,4,18,2,19],[20,4,16,4,17],[24,6,19,2,20],[28,4,22,4,23],[26,4,20,6,21],[24,8,20,4,21],[20,11,16,5,17],[30,5,24,7,25],[24,15,19,2,20],[28,1,22,15,23],[28,17,22,1,23],[26,17,21,4,22],[30,15,24,5,25]],
  H: [[17,1,9,0,0],[28,1,16,0,0],[22,2,13,0,0],[16,4,9,0,0],[22,2,11,2,12],[28,4,15,0,0],[26,4,13,1,14],[26,4,14,2,15],[24,4,12,4,13],[28,6,15,2,16],[24,3,12,8,13],[28,7,14,4,15],[22,12,11,4,12],[24,11,12,5,13],[24,11,12,7,13],[30,3,15,13,16],[28,2,14,17,15],[28,2,14,19,15],[26,9,13,16,14],[28,15,15,10,16]]
};
const ALIGN_POS = [[],[6,18],[6,22],[6,26],[6,30],[6,34],[6,22,38],[6,24,42],[6,26,46],[6,28,50],[6,30,54],[6,32,58],[6,34,62],[6,26,46,66],[6,26,48,70],[6,26,50,74],[6,30,54,78],[6,30,56,82],[6,30,58,86],[6,34,62,90]];
const TOTAL_CODEWORDS = [26,44,70,100,134,172,196,242,292,346,404,466,532,581,655,733,815,901,991,1085];

const versionSize = v => v * 4 + 17;
function dataCapacityBytes(v, ecl) {
  const [, b1, d1, b2, d2] = EC_TABLE[ecl][v - 1];
  return b1 * d1 + b2 * d2;
}

/* ========================== Codificacion de datos ========================= */
function buildBitStream(bytes, version, ecl) {
  const bits = [];
  const push = (val, len) => { for (let i = len - 1; i >= 0; i--) bits.push((val >> i) & 1); };
  push(0b0100, 4);
  push(bytes.length, version <= 9 ? 8 : 16);
  for (const b of bytes) push(b, 8);

  const capacityBits = dataCapacityBytes(version, ecl) * 8;
  for (let i = 0; i < 4 && bits.length < capacityBits; i++) bits.push(0);
  while (bits.length % 8 !== 0) bits.push(0);

  const out = [];
  for (let i = 0; i < bits.length; i += 8) {
    let byte = 0;
    for (let j = 0; j < 8; j++) byte = (byte << 1) | bits[i + j];
    out.push(byte);
  }
  const pad = [0xEC, 0x11];
  let k = 0;
  while (out.length < dataCapacityBytes(version, ecl)) out.push(pad[k++ % 2]);
  return out;
}

function interleave(dataBytes, version, ecl) {
  const [ecLen, b1, d1, b2, d2] = EC_TABLE[ecl][version - 1];
  const blocks = [], ecBlocks = [];
  let offset = 0;
  for (let i = 0; i < b1; i++) { blocks.push(dataBytes.slice(offset, offset + d1)); offset += d1; }
  for (let i = 0; i < b2; i++) { blocks.push(dataBytes.slice(offset, offset + d2)); offset += d2; }
  for (const blk of blocks) ecBlocks.push(rsEncode(Uint8Array.from(blk), ecLen));

  const result = [];
  const maxData = Math.max(d1, d2);
  for (let i = 0; i < maxData; i++) for (const blk of blocks) if (i < blk.length) result.push(blk[i]);
  for (let i = 0; i < ecLen; i++) for (const blk of ecBlocks) result.push(blk[i]);
  return result;
}

function formatInfo(ecl, mask) {
  const eclBits = { L: 1, M: 0, Q: 3, H: 2 }[ecl];
  const data = (eclBits << 3) | mask;
  let rem = data;
  for (let i = 0; i < 10; i++) rem = (rem << 1) ^ (((rem >> 9) & 1) * 0x537);
  return ((data << 10) | rem) ^ 0x5412;
}
function versionInfo(v) {
  let rem = v;
  for (let i = 0; i < 12; i++) rem = (rem << 1) ^ (((rem >> 11) & 1) * 0x1F25);
  return (v << 12) | rem;
}

/* ============================ Matriz y mascaras =========================== */
function buildMatrix(version, codewords) {
  const size = versionSize(version);
  const m = Array.from({ length: size }, () => new Array(size).fill(null));
  const reserved = Array.from({ length: size }, () => new Array(size).fill(false));
  const set = (r, c, v) => { if (r >= 0 && r < size && c >= 0 && c < size) { m[r][c] = v; reserved[r][c] = true; } };

  const finder = (row, col) => {
    for (let r = -1; r <= 7; r++) for (let c = -1; c <= 7; c++) {
      const inner = r >= 0 && r <= 6 && c >= 0 && c <= 6 &&
        (r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
      set(row + r, col + c, inner ? 1 : 0);
    }
  };
  finder(0, 0); finder(0, size - 7); finder(size - 7, 0);

  for (const r of ALIGN_POS[version - 1]) for (const c of ALIGN_POS[version - 1]) {
    if ((r <= 7 && c <= 7) || (r <= 7 && c >= size - 8) || (r >= size - 8 && c <= 7)) continue;
    for (let dr = -2; dr <= 2; dr++) for (let dc = -2; dc <= 2; dc++)
      set(r + dr, c + dc, (Math.abs(dr) === 2 || Math.abs(dc) === 2 || (dr === 0 && dc === 0)) ? 1 : 0);
  }

  for (let i = 8; i < size - 8; i++) { set(6, i, i % 2 === 0 ? 1 : 0); set(i, 6, i % 2 === 0 ? 1 : 0); }
  set(size - 8, 8, 1);

  for (let i = 0; i < 9; i++) {
    if (m[8][i] === null) { m[8][i] = 0; reserved[8][i] = true; }
    if (m[i][8] === null) { m[i][8] = 0; reserved[i][8] = true; }
  }
  for (let i = 0; i < 8; i++) {
    if (m[8][size - 1 - i] === null) { m[8][size - 1 - i] = 0; reserved[8][size - 1 - i] = true; }
    if (m[size - 1 - i][8] === null) { m[size - 1 - i][8] = 0; reserved[size - 1 - i][8] = true; }
  }
  if (version >= 7) {
    for (let i = 0; i < 6; i++) for (let j = 0; j < 3; j++) {
      m[size - 11 + j][i] = 0; reserved[size - 11 + j][i] = true;
      m[i][size - 11 + j] = 0; reserved[i][size - 11 + j] = true;
    }
  }

  let bitIdx = 0, dir = -1, row = size - 1;
  for (let col = size - 1; col > 0; col -= 2) {
    if (col === 6) col--;
    while (row >= 0 && row < size) {
      for (let i = 0; i < 2; i++) {
        const c = col - i;
        if (!reserved[row][c]) {
          const byte = codewords[bitIdx >> 3];
          m[row][c] = byte === undefined ? 0 : (byte >> (7 - (bitIdx & 7))) & 1;
          bitIdx++;
        }
      }
      row += dir;
    }
    dir = -dir; row += dir;
  }
  return { m, reserved, size };
}

const MASKS = [
  (r, c) => (r + c) % 2 === 0,
  (r, c) => r % 2 === 0,
  (r, c) => c % 3 === 0,
  (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
  (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
  (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
  (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0
];

function applyMaskAndFormat(base, version, ecl, mask) {
  const { m, reserved, size } = base;
  const out = m.map(r => r.slice());
  for (let r = 0; r < size; r++) for (let c = 0; c < size; c++)
    if (!reserved[r][c] && MASKS[mask](r, c)) out[r][c] ^= 1;

  const fmt = formatInfo(ecl, mask);
  for (let i = 0; i < 15; i++) {
    const bit = (fmt >> i) & 1;
    if (i < 6) out[8][i] = bit;
    else if (i === 6) out[8][7] = bit;
    else if (i === 7) out[8][8] = bit;
    else if (i === 8) out[7][8] = bit;
    else out[14 - i][8] = bit;
    if (i < 8) out[size - 1 - i][8] = bit;
    else out[8][size - 15 + i] = bit;
  }
  out[size - 8][8] = 1;

  if (version >= 7) {
    const vi = versionInfo(version);
    for (let i = 0; i < 18; i++) {
      const bit = (vi >> i) & 1;
      const a = Math.floor(i / 3), b = i % 3;
      out[size - 11 + b][a] = bit;
      out[a][size - 11 + b] = bit;
    }
  }
  return out;
}

function penalty(m) {
  const n = m.length;
  let score = 0;
  const runScore = line => {
    let s = 0, run = 1;
    for (let i = 1; i < n; i++) {
      if (line[i] === line[i - 1]) run++;
      else { if (run >= 5) s += 3 + (run - 5); run = 1; }
    }
    if (run >= 5) s += 3 + (run - 5);
    return s;
  };
  for (let r = 0; r < n; r++) score += runScore(m[r]);
  for (let c = 0; c < n; c++) score += runScore(m.map(row => row[c]));
  for (let r = 0; r < n - 1; r++) for (let c = 0; c < n - 1; c++) {
    const v = m[r][c];
    if (v === m[r][c + 1] && v === m[r + 1][c] && v === m[r + 1][c + 1]) score += 3;
  }
  const p1 = [1,0,1,1,1,0,1,0,0,0,0], p2 = [0,0,0,0,1,0,1,1,1,0,1];
  const match = (arr, i, p) => p.every((v, k) => arr[i + k] === v);
  for (let r = 0; r < n; r++) for (let c = 0; c <= n - 11; c++)
    if (match(m[r], c, p1) || match(m[r], c, p2)) score += 40;
  for (let c = 0; c < n; c++) {
    const col = m.map(row => row[c]);
    for (let r = 0; r <= n - 11; r++) if (match(col, r, p1) || match(col, r, p2)) score += 40;
  }
  let dark = 0;
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) dark += m[r][c];
  score += Math.floor(Math.abs((dark * 100) / (n * n) - 50) / 5) * 10;
  return score;
}

function generateQR(text, ecl) {
  const bytes = Buffer.from(text, 'utf8');
  let version = 0;
  for (let v = 1; v <= 20; v++) {
    const header = 4 + (v <= 9 ? 8 : 16);
    if (bytes.length * 8 + header <= dataCapacityBytes(v, ecl) * 8) { version = v; break; }
  }
  if (!version) throw new Error('Texto demasiado largo para un QR de version 20 con nivel ' + ecl);

  const codewords = interleave(buildBitStream(bytes, version, ecl), version, ecl);
  const base = buildMatrix(version, codewords);
  let best = null, bestScore = Infinity, bestMask = -1;
  for (let mask = 0; mask < 8; mask++) {
    const cand = applyMaskAndFormat(base, version, ecl, mask);
    const s = penalty(cand);
    if (s < bestScore) { bestScore = s; best = cand; bestMask = mask; }
  }
  return { matrix: best, version, mask: bestMask, ecl };
}

/* =============================== PNG a mano ============================== */
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

/** PNG en escala de grises de 8 bits: lo mas simple y compatible que existe. */
function writePNG(matrix, scale, quiet, file) {
  const n = matrix.length;
  const size = (n + quiet * 2) * scale;
  const raw = Buffer.alloc((size + 1) * size);
  let p = 0;
  for (let y = 0; y < size; y++) {
    raw[p++] = 0; // filtro "none"
    const mr = Math.floor(y / scale) - quiet;
    for (let x = 0; x < size; x++) {
      const mc = Math.floor(x / scale) - quiet;
      const dark = mr >= 0 && mr < n && mc >= 0 && mc < n && matrix[mr][mc] === 1;
      raw[p++] = dark ? 0x00 : 0xFF;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 0; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;

  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0))
  ]);
  fs.writeFileSync(file, png);
  return { file, size };
}

function toAscii(matrix) {
  const n = matrix.length, pad = 2;
  const rows = [];
  for (let r = -pad; r < n + pad; r += 2) {
    let line = '';
    for (let c = -pad; c < n + pad; c++) {
      const top = r >= 0 && r < n && c >= 0 && c < n && matrix[r][c] === 1;
      const bot = r + 1 >= 0 && r + 1 < n && c >= 0 && c < n && matrix[r + 1][c] === 1;
      line += top && bot ? '█' : top ? '▀' : bot ? '▄' : ' ';
    }
    rows.push(line);
  }
  return rows.join('\n');
}

/* ======================= Decodificador de verificacion ===================
 * Recorre el camino inverso: lee el formato de la matriz, quita la mascara,
 * recupera los codewords, comprueba que los sindromes de Reed-Solomon son
 * cero y reconstruye el texto. Si esto cuadra, un escaner real lo lee.
 * ======================================================================== */
function decodeQR(matrix) {
  const size = matrix.length;
  const version = (size - 17) / 4;
  if (!Number.isInteger(version) || version < 1 || version > 20) throw new Error('Tamano de matriz invalido');

  // 1. Leer los 15 bits de formato y deshacer la mascara del estandar
  let fmtBits = 0;
  for (let i = 0; i < 15; i++) {
    let bit;
    if (i < 6) bit = matrix[8][i];
    else if (i === 6) bit = matrix[8][7];
    else if (i === 7) bit = matrix[8][8];
    else if (i === 8) bit = matrix[7][8];
    else bit = matrix[14 - i][8];
    fmtBits |= bit << i;
  }
  const unmasked = fmtBits ^ 0x5412;
  const eclBits = (unmasked >> 13) & 3;
  const mask = (unmasked >> 10) & 7;
  const ecl = { 1: 'L', 0: 'M', 3: 'Q', 2: 'H' }[eclBits];
  if (!ecl) throw new Error('Nivel de correccion ilegible: ' + eclBits);

  // 2. Reconstruir el mapa de zonas reservadas y quitar la mascara
  const { reserved } = buildMatrix(version, []);
  const plain = matrix.map(r => r.slice());
  for (let r = 0; r < size; r++) for (let c = 0; c < size; c++)
    if (!reserved[r][c] && MASKS[mask](r, c)) plain[r][c] ^= 1;

  // 3. Leer los bits en zigzag, igual que al escribir pero al reves
  const bits = [];
  let dir = -1, row = size - 1;
  for (let col = size - 1; col > 0; col -= 2) {
    if (col === 6) col--;
    while (row >= 0 && row < size) {
      for (let i = 0; i < 2; i++) {
        const c = col - i;
        if (!reserved[row][c]) bits.push(plain[row][c]);
      }
      row += dir;
    }
    dir = -dir; row += dir;
  }
  const stream = [];
  for (let i = 0; i + 7 < bits.length; i += 8) {
    let b = 0;
    for (let j = 0; j < 8; j++) b = (b << 1) | bits[i + j];
    stream.push(b);
  }

  // 4. Deshacer el intercalado
  const [ecLen, b1, d1, b2, d2] = EC_TABLE[ecl][version - 1];
  const nBlocks = b1 + b2;
  const dataSizes = [];
  for (let i = 0; i < b1; i++) dataSizes.push(d1);
  for (let i = 0; i < b2; i++) dataSizes.push(d2);

  const dataBlocks = dataSizes.map(n => []);
  let idx = 0;
  const maxData = Math.max(d1, d2 || 0);
  for (let i = 0; i < maxData; i++)
    for (let b = 0; b < nBlocks; b++)
      if (i < dataSizes[b]) dataBlocks[b].push(stream[idx++]);

  const ecBlocks = Array.from({ length: nBlocks }, () => []);
  for (let i = 0; i < ecLen; i++)
    for (let b = 0; b < nBlocks; b++) ecBlocks[b].push(stream[idx++]);

  // 5. Sindromes de Reed-Solomon: si hay algun byte mal, salen distintos de cero
  let syndromeErrors = 0;
  for (let b = 0; b < nBlocks; b++) {
    const full = dataBlocks[b].concat(ecBlocks[b]);
    for (let s = 0; s < ecLen; s++) {
      let acc = 0;
      for (let i = 0; i < full.length; i++) acc = gmul(acc, EXP[s]) ^ full[i];
      if (acc !== 0) syndromeErrors++;
    }
  }

  // 6. Reconstruir el texto original
  const allData = [];
  for (const blk of dataBlocks) allData.push(...blk);
  const bitArr = [];
  for (const byte of allData) for (let i = 7; i >= 0; i--) bitArr.push((byte >> i) & 1);
  const take = n => { let v = 0; for (let i = 0; i < n; i++) v = (v << 1) | bitArr.shift(); return v; };

  const mode = take(4);
  if (mode !== 0b0100) throw new Error('Modo inesperado: ' + mode.toString(2));
  const count = take(version <= 9 ? 8 : 16);
  const out = [];
  for (let i = 0; i < count; i++) out.push(take(8));

  return {
    text: Buffer.from(out).toString('utf8'),
    version, ecl, mask, syndromeErrors, blocks: nBlocks
  };
}

/* ============================== Verificacion ============================= */
function selfTest() {
  const results = [];
  const ok = (name, pass, detail) => results.push((pass ? '  OK    ' : '  FALLO ') + name + (detail ? ' -> ' + detail : ''));

  // Reed-Solomon contra el ejemplo publicado en la norma
  const data = [16,32,12,86,97,128,236,17,236,17,236,17,236,17,236,17];
  const want = [165,36,212,193,237,54,199,135,44,85];
  const got = Array.from(rsEncode(Uint8Array.from(data), 10));
  ok('Reed-Solomon (ejemplo ISO 18004)', JSON.stringify(got) === JSON.stringify(want), got.join(','));

  // Informacion de formato publicada
  const fmt = [['M',0,0x5412],['M',1,0x5125],['M',2,0x5E7C],['M',3,0x5B4B],['L',0,0x77C4],['L',1,0x72F3],['Q',0,0x355F],['H',0,0x1689]];
  ok('Informacion de formato (8 valores)', fmt.every(([e,m,x]) => formatInfo(e,m) === x));

  // Informacion de version publicada
  const ver = { 7:0x07C94, 8:0x085BC, 9:0x09A99, 10:0x0A4D3, 11:0x0BBF6, 12:0x0C762 };
  ok('Informacion de version (6 valores)', Object.keys(ver).every(v => versionInfo(+v) === ver[v]));

  // Coherencia interna de toda la tabla de correccion de errores
  let bad = [];
  for (const ecl of ['L','M','Q','H']) for (let v = 1; v <= 20; v++) {
    const [ec, b1, d1, b2, d2] = EC_TABLE[ecl][v-1];
    if (b1*d1 + b2*d2 + (b1+b2)*ec !== TOTAL_CODEWORDS[v-1]) bad.push(ecl + v);
  }
  ok('Tabla EC coherente (80 combinaciones)', bad.length === 0, bad.join(','));

  // Estructura del QR resultante
  const { matrix } = generateQR('https://github.com/u/r/releases/download/v1.0.0/app.apk', 'M');
  const n = matrix.length;
  const finderOK = (r0,c0) => {
    for (let r = 0; r < 7; r++) for (let c = 0; c < 7; c++) {
      const want = (r===0||r===6||c===0||c===6||(r>=2&&r<=4&&c>=2&&c<=4)) ? 1 : 0;
      if (matrix[r0+r][c0+c] !== want) return false;
    }
    return true;
  };
  ok('Patrones localizadores', finderOK(0,0) && finderOK(0,n-7) && finderOK(n-7,0));
  let timing = true;
  for (let i = 8; i < n-8; i++) if (matrix[6][i] !== (i%2===0?1:0) || matrix[i][6] !== (i%2===0?1:0)) timing = false;
  ok('Patrones de sincronizacion', timing);
  ok('Modulo oscuro obligatorio', matrix[n-8][8] === 1);
  let nulls = 0;
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (matrix[r][c] === null) nulls++;
  ok('Sin celdas sin asignar', nulls === 0, String(nulls));

  // Barrido dentro de la capacidad real de cada nivel: nada debe fallar.
  let errs = 0, swept = 0;
  for (const ecl of ['L','M','Q','H']) {
    const maxBytes = dataCapacityBytes(20, ecl) - 3; // menos cabecera de modo y contador
    for (let len = 1; len <= maxBytes; len += 7) {
      swept++;
      try { generateQR('x'.repeat(len), ecl); } catch (e) { errs++; }
    }
  }
  ok('Barrido dentro de capacidad (' + swept + ' casos)', errs === 0, String(errs));

  // Pasarse de capacidad debe dar un error claro, no un QR corrupto.
  let rejected = 0;
  for (const ecl of ['L','M','Q','H']) {
    try { generateQR('x'.repeat(dataCapacityBytes(20, ecl) + 50), ecl); } catch (e) { rejected++; }
  }
  ok('Rechaza textos que no caben', rejected === 4, rejected + '/4');

  // Ida y vuelta completa: codificar, decodificar y comprobar los sindromes.
  const casos = [
    'https://github.com/usuario/chispa-app/releases/download/v1.0.0/chispa-1.0.0.apk',
    'https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz',
    'HELLO WORLD',
    'https://ejemplo.es/ñandú-café-über',
    'x'.repeat(300)
  ];
  let rtBad = [], syn = 0, done = 0;
  for (const texto of casos) {
    for (const ecl of ['L','M','Q','H']) {
      let qr;
      try { qr = generateQR(texto, ecl); } catch (e) { continue; } // no cabe en ese nivel
      done++;
      const dec = decodeQR(qr.matrix);
      syn += dec.syndromeErrors;
      if (dec.text !== texto) rtBad.push(ecl + ':' + texto.slice(0, 20));
      if (dec.ecl !== ecl || dec.mask !== qr.mask) rtBad.push('meta ' + ecl);
    }
  }
  ok('Ida y vuelta codificar/decodificar (' + done + ' casos)', rtBad.length === 0, rtBad.join(' '));
  ok('Sindromes Reed-Solomon a cero', syn === 0, String(syn));

  const failed = results.filter(r => r.startsWith('  FALLO')).length;
  console.log('\nVerificacion del generador de QR:\n' + results.join('\n'));
  console.log(failed === 0 ? '\nTodo correcto.\n' : '\n' + failed + ' comprobacion(es) fallaron.\n');
  return failed === 0;
}

/* ================================== CLI ================================== */
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes('--test')) { process.exit(selfTest() ? 0 : 1); }

  const positional = argv.filter(a => !a.startsWith('--'));
  const flag = (name, def) => {
    const i = argv.indexOf('--' + name);
    return i >= 0 && argv[i + 1] ? argv[i + 1] : def;
  };

  const text = positional[0];
  if (!text) {
    console.error('Uso: node qr.js "<texto o URL>" [salida.png] [--ecl M] [--scale 10]');
    process.exit(1);
  }
  const outFile = positional[1] || 'qr.png';
  const ecl = (flag('ecl', 'M')).toUpperCase();
  const scale = parseInt(flag('scale', '10'), 10);

  if (!['L','M','Q','H'].includes(ecl)) { console.error('El nivel debe ser L, M, Q o H'); process.exit(1); }

  const qr = generateQR(text, ecl);
  const { file, size } = writePNG(qr.matrix, scale, 4, outFile);

  console.log(toAscii(qr.matrix));
  console.log('');
  console.log('Contenido : ' + text);
  console.log('Version   : ' + qr.version + ' (' + qr.matrix.length + 'x' + qr.matrix.length + ' modulos), nivel ' + qr.ecl + ', mascara ' + qr.mask);
  console.log('Imagen    : ' + file + ' (' + size + 'x' + size + ' px)');
}

if (require.main === module) main();
module.exports = { generateQR, writePNG, toAscii, selfTest };
