# Bundled font provenance

The font and license files under `fonts/` are committed verbatim from the
upstream Google Fonts repository. Measured 2026-09-19.

| file | bytes | sha256 | version |
|---|---|---|---|
| `fonts/roboto/Roboto[wdth,wght].ttf` | 488,584 | `d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134` | 3.015 |
| `fonts/roboto-mono/RobotoMono[wght].ttf` | 183,700 | `66a80e79d17e4c7cabd162e2916578a4cc08fd19eef6e2a643305eae9c567b2b` | 3.001 |
| `fonts/roboto/OFL.txt` | 4,394 | `061402327a96aadb0bfb694a960ed289ecd38d383e396243831ab81feb109c41` | — |
| `fonts/roboto-mono/OFL.txt` | 4,395 | `50ab8dd54680d3473f649c9db86fece88434d097c7834475c1c72d2f8c429215` | — |

Both families are licensed under the SIL Open Font License, Version 1.1
(`OFL-1.1`). Each family's license text ships beside its font, is embedded in
the generated accessor, and is copied to
`src/jvmMain/resources/META-INF/licenses/<family>/OFL.txt` so the JVM jar
carries it as a real file.

Upstream sources:

- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/OFL.txt
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/RobotoMono%5Bwght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/OFL.txt
