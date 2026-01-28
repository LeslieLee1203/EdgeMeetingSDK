/**
 * bpe_unicode_map.h
 *
 * GPT-2 Byte-Level BPE Unicode-to-Byte 映射表
 * 用於 Whisper tokenizer 解碼
 *
 * 此映射表將 Whisper vocab 中的 Unicode 字符反向映射為原始字節值（0-255）
 * 例如：'è' (U+00E8) → 0xE8, 'Ļ' (U+013B) → 0x99
 */

#ifndef BPE_UNICODE_MAP_H
#define BPE_UNICODE_MAP_H

#include <unordered_map>
#include <cstdint>

namespace whisper {

/**
 * Unicode code point 到原始字節的映射表
 * 總共 256 個映射（0x00-0xFF）
 */
static const std::unordered_map<uint32_t, uint8_t> UNICODE_TO_BYTE_MAP = {
    {0x0021, 0x21},  // '!' → byte 33
    {0x0022, 0x22},  // '"' → byte 34
    {0x0023, 0x23},  // '#' → byte 35
    {0x0024, 0x24},  // '$' → byte 36
    {0x0025, 0x25},  // '%' → byte 37
    {0x0026, 0x26},  // '&' → byte 38
    {0x0027, 0x27},  // ''' → byte 39
    {0x0028, 0x28},  // '(' → byte 40
    {0x0029, 0x29},  // ')' → byte 41
    {0x002A, 0x2A},  // '*' → byte 42
    {0x002B, 0x2B},  // '+' → byte 43
    {0x002C, 0x2C},  // ',' → byte 44
    {0x002D, 0x2D},  // '-' → byte 45
    {0x002E, 0x2E},  // '.' → byte 46
    {0x002F, 0x2F},  // '/' → byte 47
    {0x0030, 0x30},  // '0' → byte 48
    {0x0031, 0x31},  // '1' → byte 49
    {0x0032, 0x32},  // '2' → byte 50
    {0x0033, 0x33},  // '3' → byte 51
    {0x0034, 0x34},  // '4' → byte 52
    {0x0035, 0x35},  // '5' → byte 53
    {0x0036, 0x36},  // '6' → byte 54
    {0x0037, 0x37},  // '7' → byte 55
    {0x0038, 0x38},  // '8' → byte 56
    {0x0039, 0x39},  // '9' → byte 57
    {0x003A, 0x3A},  // ':' → byte 58
    {0x003B, 0x3B},  // ';' → byte 59
    {0x003C, 0x3C},  // '<' → byte 60
    {0x003D, 0x3D},  // '=' → byte 61
    {0x003E, 0x3E},  // '>' → byte 62
    {0x003F, 0x3F},  // '?' → byte 63
    {0x0040, 0x40},  // '@' → byte 64
    {0x0041, 0x41},  // 'A' → byte 65
    {0x0042, 0x42},  // 'B' → byte 66
    {0x0043, 0x43},  // 'C' → byte 67
    {0x0044, 0x44},  // 'D' → byte 68
    {0x0045, 0x45},  // 'E' → byte 69
    {0x0046, 0x46},  // 'F' → byte 70
    {0x0047, 0x47},  // 'G' → byte 71
    {0x0048, 0x48},  // 'H' → byte 72
    {0x0049, 0x49},  // 'I' → byte 73
    {0x004A, 0x4A},  // 'J' → byte 74
    {0x004B, 0x4B},  // 'K' → byte 75
    {0x004C, 0x4C},  // 'L' → byte 76
    {0x004D, 0x4D},  // 'M' → byte 77
    {0x004E, 0x4E},  // 'N' → byte 78
    {0x004F, 0x4F},  // 'O' → byte 79
    {0x0050, 0x50},  // 'P' → byte 80
    {0x0051, 0x51},  // 'Q' → byte 81
    {0x0052, 0x52},  // 'R' → byte 82
    {0x0053, 0x53},  // 'S' → byte 83
    {0x0054, 0x54},  // 'T' → byte 84
    {0x0055, 0x55},  // 'U' → byte 85
    {0x0056, 0x56},  // 'V' → byte 86
    {0x0057, 0x57},  // 'W' → byte 87
    {0x0058, 0x58},  // 'X' → byte 88
    {0x0059, 0x59},  // 'Y' → byte 89
    {0x005A, 0x5A},  // 'Z' → byte 90
    {0x005B, 0x5B},  // '[' → byte 91
    {0x005C, 0x5C},  // '\' → byte 92
    {0x005D, 0x5D},  // ']' → byte 93
    {0x005E, 0x5E},  // '^' → byte 94
    {0x005F, 0x5F},  // '_' → byte 95
    {0x0060, 0x60},  // '`' → byte 96
    {0x0061, 0x61},  // 'a' → byte 97
    {0x0062, 0x62},  // 'b' → byte 98
    {0x0063, 0x63},  // 'c' → byte 99
    {0x0064, 0x64},  // 'd' → byte 100
    {0x0065, 0x65},  // 'e' → byte 101
    {0x0066, 0x66},  // 'f' → byte 102
    {0x0067, 0x67},  // 'g' → byte 103
    {0x0068, 0x68},  // 'h' → byte 104
    {0x0069, 0x69},  // 'i' → byte 105
    {0x006A, 0x6A},  // 'j' → byte 106
    {0x006B, 0x6B},  // 'k' → byte 107
    {0x006C, 0x6C},  // 'l' → byte 108
    {0x006D, 0x6D},  // 'm' → byte 109
    {0x006E, 0x6E},  // 'n' → byte 110
    {0x006F, 0x6F},  // 'o' → byte 111
    {0x0070, 0x70},  // 'p' → byte 112
    {0x0071, 0x71},  // 'q' → byte 113
    {0x0072, 0x72},  // 'r' → byte 114
    {0x0073, 0x73},  // 's' → byte 115
    {0x0074, 0x74},  // 't' → byte 116
    {0x0075, 0x75},  // 'u' → byte 117
    {0x0076, 0x76},  // 'v' → byte 118
    {0x0077, 0x77},  // 'w' → byte 119
    {0x0078, 0x78},  // 'x' → byte 120
    {0x0079, 0x79},  // 'y' → byte 121
    {0x007A, 0x7A},  // 'z' → byte 122
    {0x007B, 0x7B},  // '{' → byte 123
    {0x007C, 0x7C},  // '|' → byte 124
    {0x007D, 0x7D},  // '}' → byte 125
    {0x007E, 0x7E},  // '~' → byte 126
    {0x00A1, 0xA1},  // '¡' → byte 161
    {0x00A2, 0xA2},  // '¢' → byte 162
    {0x00A3, 0xA3},  // '£' → byte 163
    {0x00A4, 0xA4},  // '¤' → byte 164
    {0x00A5, 0xA5},  // '¥' → byte 165
    {0x00A6, 0xA6},  // '¦' → byte 166
    {0x00A7, 0xA7},  // '§' → byte 167
    {0x00A8, 0xA8},  // '¨' → byte 168
    {0x00A9, 0xA9},  // '©' → byte 169
    {0x00AA, 0xAA},  // 'ª' → byte 170
    {0x00AB, 0xAB},  // '«' → byte 171
    {0x00AC, 0xAC},  // '¬' → byte 172
    {0x00AE, 0xAE},  // '®' → byte 174
    {0x00AF, 0xAF},  // '¯' → byte 175
    {0x00B0, 0xB0},  // '°' → byte 176
    {0x00B1, 0xB1},  // '±' → byte 177
    {0x00B2, 0xB2},  // '²' → byte 178
    {0x00B3, 0xB3},  // '³' → byte 179
    {0x00B4, 0xB4},  // '´' → byte 180
    {0x00B5, 0xB5},  // 'µ' → byte 181
    {0x00B6, 0xB6},  // '¶' → byte 182
    {0x00B7, 0xB7},  // '·' → byte 183
    {0x00B8, 0xB8},  // '¸' → byte 184
    {0x00B9, 0xB9},  // '¹' → byte 185
    {0x00BA, 0xBA},  // 'º' → byte 186
    {0x00BB, 0xBB},  // '»' → byte 187
    {0x00BC, 0xBC},  // '¼' → byte 188
    {0x00BD, 0xBD},  // '½' → byte 189
    {0x00BE, 0xBE},  // '¾' → byte 190
    {0x00BF, 0xBF},  // '¿' → byte 191
    {0x00C0, 0xC0},  // 'À' → byte 192
    {0x00C1, 0xC1},  // 'Á' → byte 193
    {0x00C2, 0xC2},  // 'Â' → byte 194
    {0x00C3, 0xC3},  // 'Ã' → byte 195
    {0x00C4, 0xC4},  // 'Ä' → byte 196
    {0x00C5, 0xC5},  // 'Å' → byte 197
    {0x00C6, 0xC6},  // 'Æ' → byte 198
    {0x00C7, 0xC7},  // 'Ç' → byte 199
    {0x00C8, 0xC8},  // 'È' → byte 200
    {0x00C9, 0xC9},  // 'É' → byte 201
    {0x00CA, 0xCA},  // 'Ê' → byte 202
    {0x00CB, 0xCB},  // 'Ë' → byte 203
    {0x00CC, 0xCC},  // 'Ì' → byte 204
    {0x00CD, 0xCD},  // 'Í' → byte 205
    {0x00CE, 0xCE},  // 'Î' → byte 206
    {0x00CF, 0xCF},  // 'Ï' → byte 207
    {0x00D0, 0xD0},  // 'Ð' → byte 208
    {0x00D1, 0xD1},  // 'Ñ' → byte 209
    {0x00D2, 0xD2},  // 'Ò' → byte 210
    {0x00D3, 0xD3},  // 'Ó' → byte 211
    {0x00D4, 0xD4},  // 'Ô' → byte 212
    {0x00D5, 0xD5},  // 'Õ' → byte 213
    {0x00D6, 0xD6},  // 'Ö' → byte 214
    {0x00D7, 0xD7},  // '×' → byte 215
    {0x00D8, 0xD8},  // 'Ø' → byte 216
    {0x00D9, 0xD9},  // 'Ù' → byte 217
    {0x00DA, 0xDA},  // 'Ú' → byte 218
    {0x00DB, 0xDB},  // 'Û' → byte 219
    {0x00DC, 0xDC},  // 'Ü' → byte 220
    {0x00DD, 0xDD},  // 'Ý' → byte 221
    {0x00DE, 0xDE},  // 'Þ' → byte 222
    {0x00DF, 0xDF},  // 'ß' → byte 223
    {0x00E0, 0xE0},  // 'à' → byte 224
    {0x00E1, 0xE1},  // 'á' → byte 225
    {0x00E2, 0xE2},  // 'â' → byte 226
    {0x00E3, 0xE3},  // 'ã' → byte 227
    {0x00E4, 0xE4},  // 'ä' → byte 228
    {0x00E5, 0xE5},  // 'å' → byte 229
    {0x00E6, 0xE6},  // 'æ' → byte 230
    {0x00E7, 0xE7},  // 'ç' → byte 231
    {0x00E8, 0xE8},  // 'è' → byte 232
    {0x00E9, 0xE9},  // 'é' → byte 233
    {0x00EA, 0xEA},  // 'ê' → byte 234
    {0x00EB, 0xEB},  // 'ë' → byte 235
    {0x00EC, 0xEC},  // 'ì' → byte 236
    {0x00ED, 0xED},  // 'í' → byte 237
    {0x00EE, 0xEE},  // 'î' → byte 238
    {0x00EF, 0xEF},  // 'ï' → byte 239
    {0x00F0, 0xF0},  // 'ð' → byte 240
    {0x00F1, 0xF1},  // 'ñ' → byte 241
    {0x00F2, 0xF2},  // 'ò' → byte 242
    {0x00F3, 0xF3},  // 'ó' → byte 243
    {0x00F4, 0xF4},  // 'ô' → byte 244
    {0x00F5, 0xF5},  // 'õ' → byte 245
    {0x00F6, 0xF6},  // 'ö' → byte 246
    {0x00F7, 0xF7},  // '÷' → byte 247
    {0x00F8, 0xF8},  // 'ø' → byte 248
    {0x00F9, 0xF9},  // 'ù' → byte 249
    {0x00FA, 0xFA},  // 'ú' → byte 250
    {0x00FB, 0xFB},  // 'û' → byte 251
    {0x00FC, 0xFC},  // 'ü' → byte 252
    {0x00FD, 0xFD},  // 'ý' → byte 253
    {0x00FE, 0xFE},  // 'þ' → byte 254
    {0x00FF, 0xFF},  // 'ÿ' → byte 255
    {0x0100, 0x00},  // byte 0 (control)
    {0x0101, 0x01},  // byte 1 (control)
    {0x0102, 0x02},  // byte 2 (control)
    {0x0103, 0x03},  // byte 3 (control)
    {0x0104, 0x04},  // byte 4 (control)
    {0x0105, 0x05},  // byte 5 (control)
    {0x0106, 0x06},  // byte 6 (control)
    {0x0107, 0x07},  // byte 7 (control)
    {0x0108, 0x08},  // byte 8 (control)
    {0x0109, 0x09},  // byte 9 (control)
    {0x010A, 0x0A},  // byte 10 (control)
    {0x010B, 0x0B},  // byte 11 (control)
    {0x010C, 0x0C},  // byte 12 (control)
    {0x010D, 0x0D},  // byte 13 (control)
    {0x010E, 0x0E},  // byte 14 (control)
    {0x010F, 0x0F},  // byte 15 (control)
    {0x0110, 0x10},  // byte 16 (control)
    {0x0111, 0x11},  // byte 17 (control)
    {0x0112, 0x12},  // byte 18 (control)
    {0x0113, 0x13},  // byte 19 (control)
    {0x0114, 0x14},  // byte 20 (control)
    {0x0115, 0x15},  // byte 21 (control)
    {0x0116, 0x16},  // byte 22 (control)
    {0x0117, 0x17},  // byte 23 (control)
    {0x0118, 0x18},  // byte 24 (control)
    {0x0119, 0x19},  // byte 25 (control)
    {0x011A, 0x1A},  // byte 26 (control)
    {0x011B, 0x1B},  // byte 27 (control)
    {0x011C, 0x1C},  // byte 28 (control)
    {0x011D, 0x1D},  // byte 29 (control)
    {0x011E, 0x1E},  // byte 30 (control)
    {0x011F, 0x1F},  // byte 31 (control)
    {0x0120, 0x20},  // 'Ġ' → byte 32
    {0x0121, 0x7F},  // byte 127 (control)
    {0x0122, 0x80},  // 'Ģ' → byte 128
    {0x0123, 0x81},  // 'ģ' → byte 129
    {0x0124, 0x82},  // 'Ĥ' → byte 130
    {0x0125, 0x83},  // 'ĥ' → byte 131
    {0x0126, 0x84},  // 'Ħ' → byte 132
    {0x0127, 0x85},  // 'ħ' → byte 133
    {0x0128, 0x86},  // 'Ĩ' → byte 134
    {0x0129, 0x87},  // 'ĩ' → byte 135
    {0x012A, 0x88},  // 'Ī' → byte 136
    {0x012B, 0x89},  // 'ī' → byte 137
    {0x012C, 0x8A},  // 'Ĭ' → byte 138
    {0x012D, 0x8B},  // 'ĭ' → byte 139
    {0x012E, 0x8C},  // 'Į' → byte 140
    {0x012F, 0x8D},  // 'į' → byte 141
    {0x0130, 0x8E},  // 'İ' → byte 142
    {0x0131, 0x8F},  // 'ı' → byte 143
    {0x0132, 0x90},  // 'Ĳ' → byte 144
    {0x0133, 0x91},  // 'ĳ' → byte 145
    {0x0134, 0x92},  // 'Ĵ' → byte 146
    {0x0135, 0x93},  // 'ĵ' → byte 147
    {0x0136, 0x94},  // 'Ķ' → byte 148
    {0x0137, 0x95},  // 'ķ' → byte 149
    {0x0138, 0x96},  // 'ĸ' → byte 150
    {0x0139, 0x97},  // 'Ĺ' → byte 151
    {0x013A, 0x98},  // 'ĺ' → byte 152
    {0x013B, 0x99},  // 'Ļ' → byte 153
    {0x013C, 0x9A},  // 'ļ' → byte 154
    {0x013D, 0x9B},  // 'Ľ' → byte 155
    {0x013E, 0x9C},  // 'ľ' → byte 156
    {0x013F, 0x9D},  // 'Ŀ' → byte 157
    {0x0140, 0x9E},  // 'ŀ' → byte 158
    {0x0141, 0x9F},  // 'Ł' → byte 159
    {0x0142, 0xA0},  // 'ł' → byte 160
    {0x0143, 0xAD}  // 'Ń' → byte 173
};

}  // namespace whisper

#endif  // BPE_UNICODE_MAP_H