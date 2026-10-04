# Bundled font provenance

The font and license files under `fonts/` are committed verbatim from the
upstream Google Fonts repository. Measured 2026-09-19.

| file | bytes | sha256 | version |
|---|---|---|---|
| `fonts/roboto/Roboto[wdth,wght].ttf` | 488,584 | `d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134` | 3.015 |
| `fonts/roboto/Roboto-Italic[wdth,wght].ttf` | 530,944 | `9725a847af6b460ffca162ae66d20dad48b01876137947180b42d7dcd7887182` | 3.015 |
| `fonts/roboto-mono/RobotoMono[wght].ttf` | 183,700 | `66a80e79d17e4c7cabd162e2916578a4cc08fd19eef6e2a643305eae9c567b2b` | 3.001 |
| `fonts/roboto-mono/RobotoMono-Italic[wght].ttf` | 196,792 | `49ac343bb7b070071e53f0a8a501d68d140ed98ebd0a43b6b8fb96cf22f09ff7` | 3.001 |
| `fonts/roboto/OFL.txt` | 4,394 | `061402327a96aadb0bfb694a960ed289ecd38d383e396243831ab81feb109c41` | — |
| `fonts/roboto-mono/OFL.txt` | 4,395 | `50ab8dd54680d3473f649c9db86fece88434d097c7834475c1c72d2f8c429215` | — |

Both families are licensed under the SIL Open Font License, Version 1.1
(`OFL-1.1`). Each family's license text ships beside its font, is embedded in
the generated accessor, and is copied to
`src/jvmMain/resources/META-INF/licenses/<family>/OFL.txt` so the JVM jar
carries it as a real file.

The two italic payloads were added 2026-10-04 and measured in the same run as
the committed romans, which re-hash byte-identical to their upstream copies, so
each roman/italic pair comes from a single upstream revision.

Upstream sources:

- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto-Italic%5Bwdth%2Cwght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/OFL.txt
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/RobotoMono%5Bwght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/RobotoMono-Italic%5Bwght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/OFL.txt
