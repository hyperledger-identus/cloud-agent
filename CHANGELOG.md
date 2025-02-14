## [1.40.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.39.0...cloud-agent-v1.40.0) (2024-11-05)

### Features

* Add KID to the credential-offers API - ATL-7704 ([#1320](https://github.com/hyperledger-identus/cloud-agent/issues/1320)) ([56200cf](https://github.com/hyperledger-identus/cloud-agent/commit/56200cfae6f62b823a74e67eb060face2ee3ecbc))
* add presentation-exchange endpoints ([#1365](https://github.com/hyperledger-identus/cloud-agent/issues/1365)) ([49f7ab3](https://github.com/hyperledger-identus/cloud-agent/commit/49f7ab3d0473d820c37dc7f4f944cf1c2cae2a25))
* ATL-6983 ZIO Stream Kafka PoC in background jobs ([#1339](https://github.com/hyperledger-identus/cloud-agent/issues/1339)) ([19ab426](https://github.com/hyperledger-identus/cloud-agent/commit/19ab426a191eec575ffebe6a2417f3fce538969c))
* Default Backend API to Array Of Credential Schema ([#1366](https://github.com/hyperledger-identus/cloud-agent/issues/1366)) ([693dcc4](https://github.com/hyperledger-identus/cloud-agent/commit/693dcc45274044ac9bebffe2a8dbe0b85b45b452))
* Default Object As Issuer ([#1349](https://github.com/hyperledger-identus/cloud-agent/issues/1349)) ([d29eebb](https://github.com/hyperledger-identus/cloud-agent/commit/d29eebbef29773103814528c382a3000c4f3b29b))
* Implement prism anoncreds method for schemas and credential definitions ([#1385](https://github.com/hyperledger-identus/cloud-agent/issues/1385)) ([fbee055](https://github.com/hyperledger-identus/cloud-agent/commit/fbee0554bf424acf8007c9b7088cdb0654f0d6b2))
* Issuer Replace Either By Union Type ([#1374](https://github.com/hyperledger-identus/cloud-agent/issues/1374)) ([8fc2fe3](https://github.com/hyperledger-identus/cloud-agent/commit/8fc2fe3dbed8856d21c18b7fedf89454661b34d6))
* presentation_submission validation logic ([#1332](https://github.com/hyperledger-identus/cloud-agent/issues/1332)) ([f80b3c3](https://github.com/hyperledger-identus/cloud-agent/commit/f80b3c34588437b131ce872fd86f93e75dcd035f))
* Support Array Of Credential Schema ([#1351](https://github.com/hyperledger-identus/cloud-agent/issues/1351)) ([948e314](https://github.com/hyperledger-identus/cloud-agent/commit/948e3149466b327686273825ce7858adaf8d7555))
* Test JWT OBJECT as Issuer ([#1343](https://github.com/hyperledger-identus/cloud-agent/issues/1343)) ([7208d95](https://github.com/hyperledger-identus/cloud-agent/commit/7208d955b56375b0c79c20a0237df9890ecd3580))
* VC support for Array of credential Status ([#1383](https://github.com/hyperledger-identus/cloud-agent/issues/1383)) ([ad946cf](https://github.com/hyperledger-identus/cloud-agent/commit/ad946cf3f635b882d772a00b0202b957a1cb82cb))
* VCVerification API support ARRAY or OBJECT as Credential Sc… ([#1355](https://github.com/hyperledger-identus/cloud-agent/issues/1355)) ([91cb4e7](https://github.com/hyperledger-identus/cloud-agent/commit/91cb4e7f4371a651617265279a27fefe9551887c))

### Bug Fixes

* Add key_id missing field ([#1403](https://github.com/hyperledger-identus/cloud-agent/issues/1403)) ([cbd1a03](https://github.com/hyperledger-identus/cloud-agent/commit/cbd1a03a8aa91c5a5487c54046e4d9305f9d9241))
* adjust Kotlin and TypeScript HTTP client to use the `schemaId` f… ([#1388](https://github.com/hyperledger-identus/cloud-agent/issues/1388)) ([c2da492](https://github.com/hyperledger-identus/cloud-agent/commit/c2da492131e5c545b0fefb101246c48684bc9433))
* cannot reuse the same credential-offer in oid4vci ([#1361](https://github.com/hyperledger-identus/cloud-agent/issues/1361)) ([6a0a3ea](https://github.com/hyperledger-identus/cloud-agent/commit/6a0a3ea3deef712479420ac23ef58aaafa7df78a))
* handle unsupported PIURI found in DIDComm messages accordingly ([#1399](https://github.com/hyperledger-identus/cloud-agent/issues/1399)) ([9b64793](https://github.com/hyperledger-identus/cloud-agent/commit/9b64793ee7939860973108a8b30bc0b48a840518))
* key id for jwt and sdjwt ([#1420](https://github.com/hyperledger-identus/cloud-agent/issues/1420)) ([5830a7e](https://github.com/hyperledger-identus/cloud-agent/commit/5830a7e17a72abae98faa81594421aa577eaeb24))
* oas to use any schema for json ast node ([#1372](https://github.com/hyperledger-identus/cloud-agent/issues/1372)) ([95d328e](https://github.com/hyperledger-identus/cloud-agent/commit/95d328e3420d4731817a1f91c720e2833e9de362))
* oid4vci endpoints error statuses and negative input validation ([#1384](https://github.com/hyperledger-identus/cloud-agent/issues/1384)) ([65cc9a7](https://github.com/hyperledger-identus/cloud-agent/commit/65cc9a712af722f5cb3dd36e78b088c20723097b))
* Preserve Presentation Format ([#1363](https://github.com/hyperledger-identus/cloud-agent/issues/1363)) ([c18385c](https://github.com/hyperledger-identus/cloud-agent/commit/c18385c8fdbbb0e5dbde9a03e21f4600bf5e6890))
* return 404 when create credConfig on non-existing issuer ([#1379](https://github.com/hyperledger-identus/cloud-agent/issues/1379)) ([e532ba6](https://github.com/hyperledger-identus/cloud-agent/commit/e532ba604c4e8e820345226d842d3b27813f5e66))
## [1.39.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.38.0...cloud-agent-v1.39.0) (2024-09-11)

### Features

* API Supports ValidFrom And ValidTo ([#1298](https://github.com/hyperledger-identus/cloud-agent/issues/1298)) ([b19f514](https://github.com/hyperledger-identus/cloud-agent/commit/b19f514d3e6bf762fa566dd4f0024e8732778911))
* API Supports ValidFrom And ValidTo (PART 2) ([#1302](https://github.com/hyperledger-identus/cloud-agent/issues/1302)) ([b0c58f3](https://github.com/hyperledger-identus/cloud-agent/commit/b0c58f3c3a2fd6fa9c5364f894737bf301f4dea9))
* API Supports ValidFrom And ValidTo Test ([#1305](https://github.com/hyperledger-identus/cloud-agent/issues/1305)) ([3a7984b](https://github.com/hyperledger-identus/cloud-agent/commit/3a7984b180989cad00e8511f9b564a51dae268f2))
* connection less issuance ([#1301](https://github.com/hyperledger-identus/cloud-agent/issues/1301)) ([594f7c9](https://github.com/hyperledger-identus/cloud-agent/commit/594f7c910f4cbe8990263b4154490d3ad0ed169d))
* connection less presentation ([#1285](https://github.com/hyperledger-identus/cloud-agent/issues/1285)) ([c5846d1](https://github.com/hyperledger-identus/cloud-agent/commit/c5846d15cbe1cfd4b9776ad7c259962182057e0c))
* connection less presentation expiration time ([#1294](https://github.com/hyperledger-identus/cloud-agent/issues/1294)) ([6024c97](https://github.com/hyperledger-identus/cloud-agent/commit/6024c97269ad23b99c5e238c97179da095cb91dc))
* Expose Stored Error ([#1276](https://github.com/hyperledger-identus/cloud-agent/issues/1276)) ([758fe87](https://github.com/hyperledger-identus/cloud-agent/commit/758fe87cb3c729b544a1df434c23d535162cbba9))
* Fix Object causing StatusList to fail ([#1322](https://github.com/hyperledger-identus/cloud-agent/issues/1322)) ([fb32d6e](https://github.com/hyperledger-identus/cloud-agent/commit/fb32d6eb732c9c3ac2ad7d9cf3f1a388400a4b6b))
* Handle Error Notify webhooks ATL-6934 ([#1279](https://github.com/hyperledger-identus/cloud-agent/issues/1279)) ([7c31a9d](https://github.com/hyperledger-identus/cloud-agent/commit/7c31a9d4ec3e63273f804c715ae96791b5aa50c9))
* integrate json-path in presentation definition ([#1311](https://github.com/hyperledger-identus/cloud-agent/issues/1311)) ([9ef6b09](https://github.com/hyperledger-identus/cloud-agent/commit/9ef6b09e8f09cd52cdb67003506b3ef15c58d919))
* Move ADRs to the identus-docs repo ([#1284](https://github.com/hyperledger-identus/cloud-agent/issues/1284)) ([4d5ca64](https://github.com/hyperledger-identus/cloud-agent/commit/4d5ca6419ac416c5851cc2a09b1cca1af572d55b))
* postgres metrics ([#1274](https://github.com/hyperledger-identus/cloud-agent/issues/1274)) ([cf3ccbe](https://github.com/hyperledger-identus/cloud-agent/commit/cf3ccbefdd8ab3f1f18e80303c910f72661cfc42))
* presentation-exchange model and json schema refactoring ([#1304](https://github.com/hyperledger-identus/cloud-agent/issues/1304)) ([75b2736](https://github.com/hyperledger-identus/cloud-agent/commit/75b2736b71739be7c57c582f64b6845da9a6bd8c))
* URL or Object as Issuer ([#1321](https://github.com/hyperledger-identus/cloud-agent/issues/1321)) ([0c53bba](https://github.com/hyperledger-identus/cloud-agent/commit/0c53bbaf3b7753015b5876001002b7a9ba54ccfb))

### Bug Fixes

* [#1259](https://github.com/hyperledger-identus/cloud-agent/issues/1259) make GITHUB_TOKEN optional ([#1275](https://github.com/hyperledger-identus/cloud-agent/issues/1275)) ([1c9cbd0](https://github.com/hyperledger-identus/cloud-agent/commit/1c9cbd0a124cdf5626605e1e4419130f885364a7))
* add License to the POM files [#1099](https://github.com/hyperledger-identus/cloud-agent/issues/1099) ([#1310](https://github.com/hyperledger-identus/cloud-agent/issues/1310)) ([5a7b950](https://github.com/hyperledger-identus/cloud-agent/commit/5a7b9508c9d62e57ac6a98da6726b382587478bf))
* add reportProcessingFailure back in PresentationRepository ([#1232](https://github.com/hyperledger-identus/cloud-agent/issues/1232)) ([d22745f](https://github.com/hyperledger-identus/cloud-agent/commit/d22745fc9589c71d038af60d8fc2c99d8cbd104a))
* bitString base64 encoding for revocation status list ([#1273](https://github.com/hyperledger-identus/cloud-agent/issues/1273)) ([45e0613](https://github.com/hyperledger-identus/cloud-agent/commit/45e0613ea42fb21786562c7f44b40f63cbdae6dc))
* changed IO to UIO as as underline repository doesn't throw error ([#1271](https://github.com/hyperledger-identus/cloud-agent/issues/1271)) ([2aba639](https://github.com/hyperledger-identus/cloud-agent/commit/2aba639aa77cfe293b11fabca5c458a220576435))
* cleanup and minor refactoring to remove duplicates ([#1309](https://github.com/hyperledger-identus/cloud-agent/issues/1309)) ([238492b](https://github.com/hyperledger-identus/cloud-agent/commit/238492b8af2e131ae9dde058ea6a57df5787c1b1))
* delete subject id from presentation record ([#1314](https://github.com/hyperledger-identus/cloud-agent/issues/1314)) ([b73b806](https://github.com/hyperledger-identus/cloud-agent/commit/b73b8066651f0685fd8b2fd46b1e12d4adfe4156))
* GET Requested present proof by Holder ([#1316](https://github.com/hyperledger-identus/cloud-agent/issues/1316)) ([3b3da2c](https://github.com/hyperledger-identus/cloud-agent/commit/3b3da2c3a14a7d48fca38f286153ceeca4839384))
* improve k8s keycloak bootstrapping script ([#1278](https://github.com/hyperledger-identus/cloud-agent/issues/1278)) ([cfc4ccf](https://github.com/hyperledger-identus/cloud-agent/commit/cfc4ccf3b10f2a59471f107e0b3cfc4ac568f5c4))
* migrate to quill for generic secret storage ([#1299](https://github.com/hyperledger-identus/cloud-agent/issues/1299)) ([e077cdd](https://github.com/hyperledger-identus/cloud-agent/commit/e077cdd016954d028b702f2a107e9165005beb5c))
* migrate wallet nonsecret storage to quill ([#1290](https://github.com/hyperledger-identus/cloud-agent/issues/1290)) ([525b3bc](https://github.com/hyperledger-identus/cloud-agent/commit/525b3bcb7006599d873e8a089e8f03da361e74eb))
* misc spelling ([#1288](https://github.com/hyperledger-identus/cloud-agent/issues/1288)) ([88efa9c](https://github.com/hyperledger-identus/cloud-agent/commit/88efa9ca1bc323af4cac35fb3096ac44b74e74bc))
* operation id repeated error ([#1306](https://github.com/hyperledger-identus/cloud-agent/issues/1306)) ([8e39d0b](https://github.com/hyperledger-identus/cloud-agent/commit/8e39d0bbdbd7d4d087865651c4c8ce3b16540174))
* remove `prism-agent` path from the apisixroute.yaml ([#1330](https://github.com/hyperledger-identus/cloud-agent/issues/1330)) ([82b9d1d](https://github.com/hyperledger-identus/cloud-agent/commit/82b9d1d38f0de0a381b9a6c6569411786630ca4b))
* remove deprecation warnings and optimize tests performance ([#1315](https://github.com/hyperledger-identus/cloud-agent/issues/1315)) ([7558245](https://github.com/hyperledger-identus/cloud-agent/commit/75582453454c80e35080817dbe8b1bb02c4a94b6))
* Remove type DID ([#1327](https://github.com/hyperledger-identus/cloud-agent/issues/1327)) ([1ed2a14](https://github.com/hyperledger-identus/cloud-agent/commit/1ed2a14651b6411add88f96208db6037b49ebd4a))
* rename folder from atala to identus ([#1270](https://github.com/hyperledger-identus/cloud-agent/issues/1270)) ([12660ef](https://github.com/hyperledger-identus/cloud-agent/commit/12660ef81a8f057bd63f1a6fa8cc775cc2459cb1))
* replace problematic dependency license from Apollo ([#1312](https://github.com/hyperledger-identus/cloud-agent/issues/1312)) ([11ee9df](https://github.com/hyperledger-identus/cloud-agent/commit/11ee9df88c789c36c6488ad1409c46cd43fcc7d5))
* the oob encode invitation ([#1313](https://github.com/hyperledger-identus/cloud-agent/issues/1313)) ([f2313f2](https://github.com/hyperledger-identus/cloud-agent/commit/f2313f23189879d8f2f3a548ee1b27c6ecb0c0be))
## [1.38.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.37.0...cloud-agent-v1.38.0) (2024-07-15)

### Features

* upgrade docusaurus and semantic-release packages ([de53f1d](https://github.com/hyperledger-identus/cloud-agent/commit/de53f1db15a25e4d66cba1b191fc6e591b42284b))

### Bug Fixes

* Move InMemory classes to the test moduels ([#1240](https://github.com/hyperledger-identus/cloud-agent/issues/1240)) ([823057a](https://github.com/hyperledger-identus/cloud-agent/commit/823057adaa6127eca80dba4df123f07098d34f65))
* move mocks into the test modules ([#1236](https://github.com/hyperledger-identus/cloud-agent/issues/1236)) ([df83026](https://github.com/hyperledger-identus/cloud-agent/commit/df83026704980e071f7aa158634da20fbc2527c3))
* use Put and Get for DID in doobie statement ([#1250](https://github.com/hyperledger-identus/cloud-agent/issues/1250)) ([fc1cf51](https://github.com/hyperledger-identus/cloud-agent/commit/fc1cf5157f5503143c23da54c8ea6fe78a776640))
* Wallet Management Error Handling ([#1248](https://github.com/hyperledger-identus/cloud-agent/issues/1248)) ([cfd5101](https://github.com/hyperledger-identus/cloud-agent/commit/cfd5101f18276b9f59830c47c0d7fa64b30662db))
## [1.37.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.36.1...cloud-agent-v1.37.0) (2024-07-01)

### Features

* add oidc4vci protocol MVP ([#1182](https://github.com/hyperledger-identus/cloud-agent/issues/1182)) ([3ae91dc](https://github.com/hyperledger-identus/cloud-agent/commit/3ae91dce17e5925aad8d5ce3471527889f26c6de))
* add schemaId to the issuance session ([#1199](https://github.com/hyperledger-identus/cloud-agent/issues/1199)) ([97f5d83](https://github.com/hyperledger-identus/cloud-agent/commit/97f5d833d7988d59c4fd51459ee3d48d779399a3))
* add support for EcdsaSecp256k1Signature2019Proof and fix pk encoding for EddsaJcs2022Proof ([#1127](https://github.com/hyperledger-identus/cloud-agent/issues/1127)) ([e617ded](https://github.com/hyperledger-identus/cloud-agent/commit/e617dedd962f379033ae199d40addd222bf945da))
* **agent:** add browser fingerprint label to http metrics ([#1231](https://github.com/hyperledger-identus/cloud-agent/issues/1231)) ([f090554](https://github.com/hyperledger-identus/cloud-agent/commit/f09055455df7ebc316fbc6f0e6bf610a05a278a1))
* ATL 6829 - Integrate ZIO failures and defects ADR in credential status list ([#1175](https://github.com/hyperledger-identus/cloud-agent/issues/1175)) ([dffad1d](https://github.com/hyperledger-identus/cloud-agent/commit/dffad1d0bc07248f1559651ddce82629231c6cf7))
* ATL-6832 ZIO failures and defects in entity controller ([#1203](https://github.com/hyperledger-identus/cloud-agent/issues/1203)) ([9050094](https://github.com/hyperledger-identus/cloud-agent/commit/905009490cf0c360431fa235cf26609bea9b951b))
* ATL-6833 integrate ZIO failures and defects in wallet event controller ([#1186](https://github.com/hyperledger-identus/cloud-agent/issues/1186)) ([8bc2018](https://github.com/hyperledger-identus/cloud-agent/commit/8bc2018bd1ca6d1aa679e1935cde5996602b8ae5))
* ATL-6834 Use ZIO Failures and Defects effectively in the Issue flow ([#1139](https://github.com/hyperledger-identus/cloud-agent/issues/1139)) ([ede7b77](https://github.com/hyperledger-identus/cloud-agent/commit/ede7b770341d1efd5026f7badc1f963ecfefcdef))
* Handle Error in Background Jobs - Improve the way we store errors and defects in DB ([#1218](https://github.com/hyperledger-identus/cloud-agent/issues/1218)) ([e3cadc9](https://github.com/hyperledger-identus/cloud-agent/commit/e3cadc9eb7f3adde05548345c456d1c059356a1c))
* implement ADR Use ZIO Failures and Defects Effectively - Mercury should not throw exceptions ([#1192](https://github.com/hyperledger-identus/cloud-agent/issues/1192)) ([a4ce87f](https://github.com/hyperledger-identus/cloud-agent/commit/a4ce87fd709102e0a5e597e5ba50891e01d46a51))
* improve OpenAPI's PresentProof to make more consistent ([#1130](https://github.com/hyperledger-identus/cloud-agent/issues/1130)) ([bdc5d20](https://github.com/hyperledger-identus/cloud-agent/commit/bdc5d207a6d1567b9dca88f4d72300de091d1d24))
* Integrate ZIO failures and defects ADR in DID Registrar Controller and Mercury ([#1180](https://github.com/hyperledger-identus/cloud-agent/issues/1180)) ([d8e2120](https://github.com/hyperledger-identus/cloud-agent/commit/d8e21201f2b07cd03afbba8fb668329105048ba2))
* SDJWT holder key binding ([#1185](https://github.com/hyperledger-identus/cloud-agent/issues/1185)) ([628f2f0](https://github.com/hyperledger-identus/cloud-agent/commit/628f2f07b29824a899b2c50b0d9cd1c5449bd0e7))
* use kid in the jwt proof header of OID4VCI CredentialIssue request ([#1184](https://github.com/hyperledger-identus/cloud-agent/issues/1184)) ([ee53eda](https://github.com/hyperledger-identus/cloud-agent/commit/ee53edae1fc0bb7f06770a835234052de46665f5))
* use the compact format in SD-JWT ([#1169](https://github.com/hyperledger-identus/cloud-agent/issues/1169)) ([65da651](https://github.com/hyperledger-identus/cloud-agent/commit/65da65185ebda33cab499e87196bf9ce3543b3aa))

### Bug Fixes

* avoid name and operationId conflict when creating oas client ([#1233](https://github.com/hyperledger-identus/cloud-agent/issues/1233)) ([73e8e24](https://github.com/hyperledger-identus/cloud-agent/commit/73e8e2445e038ea154b0ddd0ca93f15fa9db69b4))
* incorrect parsing of public key coordinates and enable some tests again ([#1215](https://github.com/hyperledger-identus/cloud-agent/issues/1215)) ([5398a75](https://github.com/hyperledger-identus/cloud-agent/commit/5398a75cfde7ee922588bd84608223abf389d5c1))
* KeyID from String to Opaque Type and presentation job cleanup  ([#1190](https://github.com/hyperledger-identus/cloud-agent/issues/1190)) ([b813faf](https://github.com/hyperledger-identus/cloud-agent/commit/b813faf5f076521955d4b84cab5d46d3635159a2))
* make init-script.sh idempotent [#1173](https://github.com/hyperledger-identus/cloud-agent/issues/1173) ([#1194](https://github.com/hyperledger-identus/cloud-agent/issues/1194)) ([1712062](https://github.com/hyperledger-identus/cloud-agent/commit/1712062cafcba4a46f4d190a7ef628208d21ce7b))
* migrate and repair in case of renaming issues ([#1211](https://github.com/hyperledger-identus/cloud-agent/issues/1211)) ([cb4d479](https://github.com/hyperledger-identus/cloud-agent/commit/cb4d47927c4d6f1c02e4fcf95a52e5ce074d2724))
* Present Error Handling (Part 1: Repo Changes) ([#1172](https://github.com/hyperledger-identus/cloud-agent/issues/1172)) ([13e2447](https://github.com/hyperledger-identus/cloud-agent/commit/13e244738c54bcc43bbea694acebc9b372a1ccb5))
* Present Error Handling Job ([#1204](https://github.com/hyperledger-identus/cloud-agent/issues/1204)) ([3191d8b](https://github.com/hyperledger-identus/cloud-agent/commit/3191d8b933e1e7e08ccd5dd95dbcfddb1ae8bb01))
* present error handling Part 2 ([#1177](https://github.com/hyperledger-identus/cloud-agent/issues/1177)) ([9ac6e52](https://github.com/hyperledger-identus/cloud-agent/commit/9ac6e52ec447710ae7917d8176dcb0ebdc6b2e0d))
* Verification Policy Error Handling ([#1228](https://github.com/hyperledger-identus/cloud-agent/issues/1228)) ([6117a3c](https://github.com/hyperledger-identus/cloud-agent/commit/6117a3cc00121c34a06fd680b1a0b4df4b188f49))
## [1.36.1](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.36.0...cloud-agent-v1.36.1) (2024-06-09)

### Bug Fixes

* Helm chart refactor vol2 ([#1162](https://github.com/hyperledger-identus/cloud-agent/issues/1162)) ([72fc6d1](https://github.com/hyperledger-identus/cloud-agent/commit/72fc6d1a61a4a1a5c0b1c81d3b0742538c8b9bc7))
## [1.36.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.35.1...cloud-agent-v1.36.0) (2024-06-09)

### Features

* improve ZIO failures and defects in credential definition ([#1133](https://github.com/hyperledger-identus/cloud-agent/issues/1133)) ([d6dfb72](https://github.com/hyperledger-identus/cloud-agent/commit/d6dfb72e298127b3e9d3a4c6577f62f4c8a1970a))
* Remove double Error logs in DIDController ([#1140](https://github.com/hyperledger-identus/cloud-agent/issues/1140)) ([888ebb4](https://github.com/hyperledger-identus/cloud-agent/commit/888ebb4b484224d9c73674cffdf7d00777cfb4dc))

### Bug Fixes

* Credential Defintion Error Handling Part 2 ([#1155](https://github.com/hyperledger-identus/cloud-agent/issues/1155)) ([2df5306](https://github.com/hyperledger-identus/cloud-agent/commit/2df530695522789bed6624bae8bd07433c05ddda))
* Credential Defintion Error Handling Part 2 ([#1156](https://github.com/hyperledger-identus/cloud-agent/issues/1156)) ([5755504](https://github.com/hyperledger-identus/cloud-agent/commit/57555047cd56ae31bbf14601fe8b5d96f838e033))
* Helm chart refactor ([#1160](https://github.com/hyperledger-identus/cloud-agent/issues/1160)) ([4b59112](https://github.com/hyperledger-identus/cloud-agent/commit/4b59112af649a00e17d6e5e0e927f5b972629ec9))
* pick right key type when creating corresponding issuer ([#1157](https://github.com/hyperledger-identus/cloud-agent/issues/1157)) ([22f0448](https://github.com/hyperledger-identus/cloud-agent/commit/22f0448ca878b385eada89d805c8f993c52173a2))
* Schema Error Handling ([#1138](https://github.com/hyperledger-identus/cloud-agent/issues/1138)) ([a9da840](https://github.com/hyperledger-identus/cloud-agent/commit/a9da84098bd56eeb9d64e7b2bdd78d5596cf35c5))
* Update the Holder to send the presentation only, No claims to disclose is needed separately  ([#1158](https://github.com/hyperledger-identus/cloud-agent/issues/1158)) ([9eaa5d4](https://github.com/hyperledger-identus/cloud-agent/commit/9eaa5d444665dbda00038a9032b890afcfa4bc15))
## [1.35.1](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.35.0...cloud-agent-v1.35.1) (2024-06-05)

### Bug Fixes

* Add expiration time for cloud-agent ([#1132](https://github.com/hyperledger-identus/cloud-agent/issues/1132)) ([f719120](https://github.com/hyperledger-identus/cloud-agent/commit/f719120211a83e96e6e4e282cc70e9f860d9298d))
* Chart refactor ([#1143](https://github.com/hyperledger-identus/cloud-agent/issues/1143)) ([f309a0e](https://github.com/hyperledger-identus/cloud-agent/commit/f309a0e7c2101c2567498b060ee71a6964d3b9e6))
## [1.35.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.34.0...cloud-agent-v1.35.0) (2024-06-03)

### Features

* **agent:** ATL-6839 migrate DIDComm endpoint to tapir ([#1116](https://github.com/hyperledger-identus/cloud-agent/issues/1116)) ([2f4f7c3](https://github.com/hyperledger-identus/cloud-agent/commit/2f4f7c395523763c3c7066781017430523317841))
* integrate SD JWT ([#1016](https://github.com/hyperledger-identus/cloud-agent/issues/1016)) ([9d7948f](https://github.com/hyperledger-identus/cloud-agent/commit/9d7948fc8208edf9f8c1712a0cd6902474f0814d))

### Bug Fixes

*  SemanticCheckOfClaims In Verification API ([#1124](https://github.com/hyperledger-identus/cloud-agent/issues/1124)) ([7cb4192](https://github.com/hyperledger-identus/cloud-agent/commit/7cb4192d41a779e2ba4de815b2e8ec469636e485))
*  update the jose dependency and switch back to the official library ([#1117](https://github.com/hyperledger-identus/cloud-agent/issues/1117)) ([3608aaf](https://github.com/hyperledger-identus/cloud-agent/commit/3608aafd980472cb63e5164339681fb079190dba))
* Changing .chart.name reference, adding name override ([#1129](https://github.com/hyperledger-identus/cloud-agent/issues/1129)) ([650ae3b](https://github.com/hyperledger-identus/cloud-agent/commit/650ae3ba94ae322fc745eb07658f1663b3e7e321))
* VC Verification API Doc ([#1118](https://github.com/hyperledger-identus/cloud-agent/issues/1118)) ([d70d4b7](https://github.com/hyperledger-identus/cloud-agent/commit/d70d4b74cb9f21d72e735f9650b746565068f282))
## [1.34.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.33.1...cloud-agent-v1.34.0) (2024-05-27)

### Features

* Rename helm chart and its resources ([#1104](https://github.com/hyperledger-identus/cloud-agent/issues/1104)) ([84c5cea](https://github.com/hyperledger-identus/cloud-agent/commit/84c5ceaf97ee491ebcb07b12f2cc78a4da1e2dc1))
## [1.33.1](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.33.0...cloud-agent-v1.33.1) (2024-05-21)

### Bug Fixes

* broken link for the cloud agent packages in readme file ([#1032](https://github.com/hyperledger-identus/cloud-agent/issues/1032)) ([92d17c2](https://github.com/hyperledger-identus/cloud-agent/commit/92d17c2b5f82d0ca35313ab51bd90b6f55d2cd87))
* expose new key types in rest api ([#1066](https://github.com/hyperledger-identus/cloud-agent/issues/1066)) ([9ce8d3a](https://github.com/hyperledger-identus/cloud-agent/commit/9ce8d3a8742f86c9a593c705e0f3aa472ff10987))
* rename the folder to identus for vc-jwt ([#1063](https://github.com/hyperledger-identus/cloud-agent/issues/1063)) ([364a5dc](https://github.com/hyperledger-identus/cloud-agent/commit/364a5dc7eb2b9f23b18f3775c207feff02893cbe))

### Performance Improvements

* update ts client in the performance tests, cleanup `println` ([#1041](https://github.com/hyperledger-identus/cloud-agent/issues/1041)) ([7d5ceba](https://github.com/hyperledger-identus/cloud-agent/commit/7d5cebafb34191964acfdf190c743a2ba253e883))
## [1.33.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.32.1...cloud-agent-v1.33.0) (2024-05-09)

### Features

* rename `prism-agent` to `cloud-agent` ([#1019](https://github.com/hyperledger-identus/cloud-agent/issues/1019)) ([74560da](https://github.com/hyperledger-identus/cloud-agent/commit/74560dabf59dac15ccd086edb7a77d9e5055621e))

### Bug Fixes

* integration test ([#1011](https://github.com/hyperledger-identus/cloud-agent/issues/1011)) ([d674f31](https://github.com/hyperledger-identus/cloud-agent/commit/d674f3162be44ba05d50b305be4838525d982706))
## [1.32.1](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.32.0...cloud-agent-v1.32.1) (2024-05-07)

### Bug Fixes

* expose pg_admin port on the localhost interface only ([#957](https://github.com/hyperledger-identus/cloud-agent/issues/957)) ([73674b5](https://github.com/hyperledger-identus/cloud-agent/commit/73674b5da6a41c4972ac3c45005ce768608b558e))
* Fix OneOf OpenAPI Serialization Issue ([#1010](https://github.com/hyperledger-identus/cloud-agent/issues/1010)) ([393c296](https://github.com/hyperledger-identus/cloud-agent/commit/393c29654b8d3d53071f0d2932a16ff81688ece6))
* remove prism-crypto dependency ([#1015](https://github.com/hyperledger-identus/cloud-agent/issues/1015)) ([46e594c](https://github.com/hyperledger-identus/cloud-agent/commit/46e594c21bdb43d78f41be6c803ad8b80dc89504))
* update open-api-spec and generator script and package.json ([#990](https://github.com/hyperledger-identus/cloud-agent/issues/990)) ([88c1b5e](https://github.com/hyperledger-identus/cloud-agent/commit/88c1b5eadf62ad0efcd4ee53b793bb08cce9667f))
## [1.32.0](https://github.com/hyperledger-identus/cloud-agent/compare/cloud-agent-v1.31.0...cloud-agent-v1.32.0) (2024-04-26)

### Features

* add sample maintainers.md ([#878](https://github.com/hyperledger-identus/cloud-agent/issues/878)) ([c6a41ed](https://github.com/hyperledger-identus/cloud-agent/commit/c6a41edc5466312da76283d37f12406d79449a7d))
* **agent:** verification API integration test + documentation ATL-6775 ([#986](https://github.com/hyperledger-identus/cloud-agent/issues/986)) ([9308b21](https://github.com/hyperledger-identus/cloud-agent/commit/9308b21e46d267265c7fcdbfc88b78ddd5ae6558))
* Align the repo with new name identus-cloud-agent ([#973](https://github.com/hyperledger-identus/cloud-agent/issues/973)) ([9fc7bb0](https://github.com/hyperledger-identus/cloud-agent/commit/9fc7bb07cac9aae1db8d389ec8831403f106d612))
* Configurations load improvement ([#954](https://github.com/hyperledger-identus/cloud-agent/issues/954)) ([dfb7577](https://github.com/hyperledger-identus/cloud-agent/commit/dfb75778f925a615aaec815241d964014d236777))
* **connect:** ATL-6599 Use ZIO Failures and Defects effectively + RFC-9457 in connect ([#927](https://github.com/hyperledger-identus/cloud-agent/issues/927)) ([eb898e0](https://github.com/hyperledger-identus/cloud-agent/commit/eb898e068f768507d6979a5d9bab35ef7ad4a045))
* key management for Ed25519 and X25519 ([#966](https://github.com/hyperledger-identus/cloud-agent/issues/966)) ([a0f6819](https://github.com/hyperledger-identus/cloud-agent/commit/a0f6819bb80d87c13f903d7dc1b67cb08b4687db))
* Vc Verification Api ([#975](https://github.com/hyperledger-identus/cloud-agent/issues/975)) ([f0a1f2c](https://github.com/hyperledger-identus/cloud-agent/commit/f0a1f2c1aaafb636cabcb84c599d9deaa90fd373))

### Bug Fixes

* add shared-crypto module and apollo wrapper for other key types ([#958](https://github.com/hyperledger-identus/cloud-agent/issues/958)) ([7eaa66c](https://github.com/hyperledger-identus/cloud-agent/commit/7eaa66c51904e58c309fd3bb0a8e8864c7902cb9))
* Check purpose of the keys ([#968](https://github.com/hyperledger-identus/cloud-agent/issues/968)) ([4b8e48d](https://github.com/hyperledger-identus/cloud-agent/commit/4b8e48d238b751ee8526e1440e8a0515f6b62de5))
* Integration Test ([#974](https://github.com/hyperledger-identus/cloud-agent/issues/974)) ([847eb2f](https://github.com/hyperledger-identus/cloud-agent/commit/847eb2f56ba2766ed28f6484391cbd9b3202fbe5))
* **prism-agent:** add missing 'PresentationVerificationFailed' status to REST API response enum ([#948](https://github.com/hyperledger-identus/cloud-agent/issues/948)) ([9a38cc9](https://github.com/hyperledger-identus/cloud-agent/commit/9a38cc97c18d26f13efa1c9535de1df003547a2b))
* use apollo for secp256k1 in shared-crypto ([#971](https://github.com/hyperledger-identus/cloud-agent/issues/971)) ([dd5e20b](https://github.com/hyperledger-identus/cloud-agent/commit/dd5e20bdedd004c2e0a81383d10b5eee3ae03788))
## [1.31.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.30.1...prism-agent-v1.31.0) (2024-03-20)

### Features

* add revocation for JWT credentials ([#934](https://github.com/hyperledger-identus/cloud-agent/issues/934)) ([88b7fa5](https://github.com/hyperledger-identus/cloud-agent/commit/88b7fa5c6cd92002ef355311eec6e30b63ab1dd6))
* Fix Update Schema and CredentialDef on Receive Credential ([#920](https://github.com/hyperledger-identus/cloud-agent/issues/920)) ([acbba3c](https://github.com/hyperledger-identus/cloud-agent/commit/acbba3ce92ee9e16893b4978c4a9ec4ce0757d53))

### Bug Fixes

* add Anoncreds Integration Test ([#923](https://github.com/hyperledger-identus/cloud-agent/issues/923)) ([27a157f](https://github.com/hyperledger-identus/cloud-agent/commit/27a157fb5a83a33704230b35eca80f297eec9a85))
* align keycloak version ([#936](https://github.com/hyperledger-identus/cloud-agent/issues/936)) ([c920fd6](https://github.com/hyperledger-identus/cloud-agent/commit/c920fd661d31b56466497e462fd0139755290b33))
* anoncred test ([#940](https://github.com/hyperledger-identus/cloud-agent/issues/940)) ([bb5ead1](https://github.com/hyperledger-identus/cloud-agent/commit/bb5ead1de3b6d2c3fae0019d50a5310b37a2eab3))
* **pollux:** function that allocates status list credential does not work correctly in multi threaded environment  ([#941](https://github.com/hyperledger-identus/cloud-agent/issues/941)) ([ecc3c01](https://github.com/hyperledger-identus/cloud-agent/commit/ecc3c019749a6b370771bcf62f89ea2599a521ac))
* **pollux:** Undo edit migration for revocation status lists ([#937](https://github.com/hyperledger-identus/cloud-agent/issues/937)) ([7f7585f](https://github.com/hyperledger-identus/cloud-agent/commit/7f7585f47ee197db162c75f7d118a87b99cc7d06))
* **prism-agent:** add validation for endpoint url ([#919](https://github.com/hyperledger-identus/cloud-agent/issues/919)) ([0402a87](https://github.com/hyperledger-identus/cloud-agent/commit/0402a8778eda839521c55a127934fba41c7b79ad))
## [1.30.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.30.0...prism-agent-v1.30.1) (2024-03-06)

### Bug Fixes

* allow configurable path convention for vault secrets ([#918](https://github.com/hyperledger-identus/cloud-agent/issues/918)) ([234a272](https://github.com/hyperledger-identus/cloud-agent/commit/234a2725614b05466391894f248c7175fb62c5b6))
* integration test ([#915](https://github.com/hyperledger-identus/cloud-agent/issues/915)) ([320ab6a](https://github.com/hyperledger-identus/cloud-agent/commit/320ab6a876606eb68f48fe7d78983b4e044b5084))
## [1.30.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.29.0...prism-agent-v1.30.0) (2024-03-01)

### Features

* **agent:** make the connection pool size configurable, fixes [#913](https://github.com/hyperledger-identus/cloud-agent/issues/913) ([#914](https://github.com/hyperledger-identus/cloud-agent/issues/914)) ([375fe0f](https://github.com/hyperledger-identus/cloud-agent/commit/375fe0f8ee042246aed37f40cbeb8f2042c99958))
## [1.29.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.28.0...prism-agent-v1.29.0) (2024-02-28)

### Features

* ZKP verification ([#792](https://github.com/hyperledger-identus/cloud-agent/issues/792)) ([ab1ab64](https://github.com/hyperledger-identus/cloud-agent/commit/ab1ab648b10a82b39d4bdde6e2c9693c8f7506b8))

### Bug Fixes

* correct the config environment variable name ([#905](https://github.com/hyperledger-identus/cloud-agent/issues/905)) ([d86436c](https://github.com/hyperledger-identus/cloud-agent/commit/d86436cbc58571b4167411643623f3ba975550ad))
## [1.28.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.27.0...prism-agent-v1.28.0) (2024-02-20)

### Features

* add credential def performance test ([#865](https://github.com/hyperledger-identus/cloud-agent/issues/865)) ([95064d6](https://github.com/hyperledger-identus/cloud-agent/commit/95064d617dda7d45916fbdddf20a544eea4acf4a))
* **pollux:** add Json VC schema meta validation ([#892](https://github.com/hyperledger-identus/cloud-agent/issues/892)) ([19c42b1](https://github.com/hyperledger-identus/cloud-agent/commit/19c42b10188d1e0242bdceb1f89b6410dcc05353))

### Bug Fixes

* **prism-agent:** increase http timeout communication channel closing… ([#901](https://github.com/hyperledger-identus/cloud-agent/issues/901)) ([8d3f29d](https://github.com/hyperledger-identus/cloud-agent/commit/8d3f29ddd830fe102d4bf25a0af8734730c80151))
* re-enable logging with SLF4J and add traceId ([#869](https://github.com/hyperledger-identus/cloud-agent/issues/869)) ([8f6af25](https://github.com/hyperledger-identus/cloud-agent/commit/8f6af25a8eafd27d5017096da64f89188354a2ca))
* remove oas schema format for empty repsonse body ([#902](https://github.com/hyperledger-identus/cloud-agent/issues/902)) ([5f2bb08](https://github.com/hyperledger-identus/cloud-agent/commit/5f2bb0872a156c9223ab56efbd47e812967ff582))
## [1.27.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.26.0...prism-agent-v1.27.0) (2024-02-08)

### Features

* support vault AppRole authentication ([#884](https://github.com/hyperledger-identus/cloud-agent/issues/884)) ([441f878](https://github.com/hyperledger-identus/cloud-agent/commit/441f878f0b573f350ffed14de0df164e1e260122))

### Bug Fixes

* remove hard code did:example:* ([#882](https://github.com/hyperledger-identus/cloud-agent/issues/882)) ([321faf5](https://github.com/hyperledger-identus/cloud-agent/commit/321faf5791b05f0c24dd6ed96f155aa65d06477d))
## [1.26.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.25.0...prism-agent-v1.26.0) (2024-02-06)

### Features

* interoperable schema changes ([#870](https://github.com/hyperledger-identus/cloud-agent/issues/870)) ([de49a93](https://github.com/hyperledger-identus/cloud-agent/commit/de49a9328524b32d714301f2b7961d5cd85b23c3))

### Bug Fixes

* **prism-agent:**  update didcomm peerdid library to support latest spec ([#877](https://github.com/hyperledger-identus/cloud-agent/issues/877)) ([0c42a62](https://github.com/hyperledger-identus/cloud-agent/commit/0c42a622143e35c439ae83cc3bb746515ce7401b))
## [1.25.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.24.0...prism-agent-v1.25.0) (2024-01-25)

### Features

* **prism-agent:** add JWT auth support for agent-admin role ([#840](https://github.com/hyperledger-identus/cloud-agent/issues/840)) ([3ccd56e](https://github.com/hyperledger-identus/cloud-agent/commit/3ccd56efadbbb3ea70e7ca63e9eb89564a83c02f))
* upgrade ZIO http client to improve performance ([#850](https://github.com/hyperledger-identus/cloud-agent/issues/850)) ([7aa9b4c](https://github.com/hyperledger-identus/cloud-agent/commit/7aa9b4c27f92b169c72a68cd4bb8f4afb63943d4))
## [1.24.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.23.0...prism-agent-v1.24.0) (2023-12-21)

### Features

* add configuration for gRPC usePlainText (enable TLS for gRPC) ([#823](https://github.com/hyperledger-identus/cloud-agent/issues/823)) ([b871bb5](https://github.com/hyperledger-identus/cloud-agent/commit/b871bb5e8eeeb71b3f22c38609ae8f1ff424016c))
## [1.23.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.22.0...prism-agent-v1.23.0) (2023-12-20)

### Features

* complete the integration with anoncred and fixes ([#820](https://github.com/hyperledger-identus/cloud-agent/issues/820)) ([15ff710](https://github.com/hyperledger-identus/cloud-agent/commit/15ff710c4a68c5f282e07b23098e362825fdb3b9))
* Liveness, and readiness probes ([#817](https://github.com/hyperledger-identus/cloud-agent/issues/817)) ([6e18666](https://github.com/hyperledger-identus/cloud-agent/commit/6e18666f51cca2d0f151b070c59049bcf005a450))
* Tune postgresql - helm chart ([#822](https://github.com/hyperledger-identus/cloud-agent/issues/822)) ([5fcd9fb](https://github.com/hyperledger-identus/cloud-agent/commit/5fcd9fb6301a5c7b1296d60e4b6fa3385ffe6727))

### Bug Fixes

* Change resource defaults for postgres ([#827](https://github.com/hyperledger-identus/cloud-agent/issues/827)) ([87809c4](https://github.com/hyperledger-identus/cloud-agent/commit/87809c4b4d6a3baf0afa37a2cf7ddf6c41a80eb6))
* correct OAS example ([#816](https://github.com/hyperledger-identus/cloud-agent/issues/816)) ([b1384b3](https://github.com/hyperledger-identus/cloud-agent/commit/b1384b38524060f3cfa6df39afaddcce26a5514e))
* Swithing to startupProbe from Readiness ([#821](https://github.com/hyperledger-identus/cloud-agent/issues/821)) ([22a78ec](https://github.com/hyperledger-identus/cloud-agent/commit/22a78ec09ccc84b5d5c03e8f07ff3a14c654cf2b))
## [1.22.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.21.1...prism-agent-v1.22.0) (2023-12-14)

### Features

* Consumer restricition parametarization  ([#814](https://github.com/hyperledger-identus/cloud-agent/issues/814)) ([e039576](https://github.com/hyperledger-identus/cloud-agent/commit/e039576fc0e285b80b2966c032ed91b9a8f26f60))

### Bug Fixes

* correct OAS examples ([#810](https://github.com/hyperledger-identus/cloud-agent/issues/810)) ([a0720dc](https://github.com/hyperledger-identus/cloud-agent/commit/a0720dcbaf10370dcacc1b5102df13929b40dfdb))
## [1.21.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.21.0...prism-agent-v1.21.1) (2023-12-12)

### Bug Fixes

* Option to disable apisix key auth ([#813](https://github.com/hyperledger-identus/cloud-agent/issues/813)) ([f163682](https://github.com/hyperledger-identus/cloud-agent/commit/f1636824047c0d03ce0790ede54e3a12d63dd787))
## [1.21.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.20.1...prism-agent-v1.21.0) (2023-12-12)

### Features

* env vars support through values file ([#811](https://github.com/hyperledger-identus/cloud-agent/issues/811)) ([2486dde](https://github.com/hyperledger-identus/cloud-agent/commit/2486dde9b0682504a02ad031b3e7498b2fa2ce17))
## [1.20.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.20.0...prism-agent-v1.20.1) (2023-12-06)

### Bug Fixes

* tolerations and nodeAffinity for k8s ([#808](https://github.com/hyperledger-identus/cloud-agent/issues/808)) ([7934fa4](https://github.com/hyperledger-identus/cloud-agent/commit/7934fa402ba86af6d8430208f1844fbd6ccda1bd))
## [1.20.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.19.1...prism-agent-v1.20.0) (2023-12-05)

### Features

* add nodeAffinity, tolerations, and resources to k8s deployment ([#804](https://github.com/hyperledger-identus/cloud-agent/issues/804)) ([22407a3](https://github.com/hyperledger-identus/cloud-agent/commit/22407a3103eff73d87ead9a8122f078845c11d95))
## [1.19.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.19.0...prism-agent-v1.19.1) (2023-11-29)

### Bug Fixes

* change admin auth priority and improve auth error message ([#800](https://github.com/hyperledger-identus/cloud-agent/issues/800)) ([32d4340](https://github.com/hyperledger-identus/cloud-agent/commit/32d43401a69c339f54380bd8d5dfe2fa383cb8d7))
## [1.19.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.18.0...prism-agent-v1.19.0) (2023-11-21)

### Features

* Accept goal and goalCode to create connection invitation  ([#785](https://github.com/hyperledger-identus/cloud-agent/issues/785)) ([71c776b](https://github.com/hyperledger-identus/cloud-agent/commit/71c776baa2caf3ca610508dba805f037fd7d6e29))
* **docs:** ADR for revocation status list expansion strategy ([#773](https://github.com/hyperledger-identus/cloud-agent/issues/773)) ([7ad6427](https://github.com/hyperledger-identus/cloud-agent/commit/7ad64277acb2bffe12524c4bfb68f687689b5b2e))
* Keycloak container support with clients and PermissionManagement service ([#755](https://github.com/hyperledger-identus/cloud-agent/issues/755)) ([a1846aa](https://github.com/hyperledger-identus/cloud-agent/commit/a1846aaa84202b55d48ea8556aad8cbbb8260f4d))
* **pollux:** Add migrations needed for JWT revocation ([#778](https://github.com/hyperledger-identus/cloud-agent/issues/778)) ([471956e](https://github.com/hyperledger-identus/cloud-agent/commit/471956e92893a7237cabca2fb065adb417678d37))
* **prism-agent:** add multi-tenant wallet self-service capability ([#779](https://github.com/hyperledger-identus/cloud-agent/issues/779)) ([f2e74cd](https://github.com/hyperledger-identus/cloud-agent/commit/f2e74cd1957e7d76f6dccadd02b1ca5b794d02b1))

### Bug Fixes

* check for active RLS on db application user ([#775](https://github.com/hyperledger-identus/cloud-agent/issues/775)) ([a792f43](https://github.com/hyperledger-identus/cloud-agent/commit/a792f43eaae0ec2cd30db2ea3308deded7a1a935))
* enable keycloak with pre-configured agent in helm chart ([#791](https://github.com/hyperledger-identus/cloud-agent/issues/791)) ([9a6e512](https://github.com/hyperledger-identus/cloud-agent/commit/9a6e5123e07462db66017439e8e434315af7c0f4))
* explicitly define transitive dependencies that were unresolvable ([#790](https://github.com/hyperledger-identus/cloud-agent/issues/790)) ([0647829](https://github.com/hyperledger-identus/cloud-agent/commit/0647829af813913aebd0dd3d703db7e363d44369))
* **pollux:** V16 migration is failing to add FK constraint because of type mismatch ([#782](https://github.com/hyperledger-identus/cloud-agent/issues/782)) ([c87beb0](https://github.com/hyperledger-identus/cloud-agent/commit/c87beb0478d4b3d54709e09597c42c23878d101e))
* **prism-agent:** more descriptive error response for validateDID in issue flow ([#783](https://github.com/hyperledger-identus/cloud-agent/issues/783)) ([b99a737](https://github.com/hyperledger-identus/cloud-agent/commit/b99a73718a06f4b97d933ba2e3220593f8d4e825))
* **prism-agent:** perform percent encoding on auth header for token introspection request ([#780](https://github.com/hyperledger-identus/cloud-agent/issues/780)) ([03d43c9](https://github.com/hyperledger-identus/cloud-agent/commit/03d43c98d8ab64e5b47830d95a6356f9d6dd1b82))
## [1.18.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.17.0...prism-agent-v1.18.0) (2023-10-24)

### Features

*  presentation API refactor ([#765](https://github.com/hyperledger-identus/cloud-agent/issues/765)) ([045d829](https://github.com/hyperledger-identus/cloud-agent/commit/045d8298f8865baeb13e243ed058e8e440b3f496))
* add new auth params ([#762](https://github.com/hyperledger-identus/cloud-agent/issues/762)) ([b8bfb86](https://github.com/hyperledger-identus/cloud-agent/commit/b8bfb867061c58fc12987b5405f561e8f10cb718))
* disable cors by default ([#747](https://github.com/hyperledger-identus/cloud-agent/issues/747)) ([1dd8c8b](https://github.com/hyperledger-identus/cloud-agent/commit/1dd8c8b0e9b0d2593bd1c17a95bf013192a64532))
* migrate docker image of the agent to Java 21 ([#758](https://github.com/hyperledger-identus/cloud-agent/issues/758)) ([d36dbf0](https://github.com/hyperledger-identus/cloud-agent/commit/d36dbf0dfbf45b64185e5b54aba0444d6e1ada88))
* **prism-agent:** add keycloak authorization support to endpoints ([#753](https://github.com/hyperledger-identus/cloud-agent/issues/753)) ([3e7534f](https://github.com/hyperledger-identus/cloud-agent/commit/3e7534ff1a75e9ecaa0c2b670c1c158890021f8d))

### Bug Fixes

*  all performance tests run succesfully, add group thresholds ([#750](https://github.com/hyperledger-identus/cloud-agent/issues/750)) ([5204838](https://github.com/hyperledger-identus/cloud-agent/commit/520483836e5b572e8aeeecd28f4bbe7cc668c3d9))
## [1.17.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.16.4...prism-agent-v1.17.0) (2023-10-14)

### Features

* **prism-agent:** check issuing DID validity when creating a VC offer + return 'metaRetries' ([#740](https://github.com/hyperledger-identus/cloud-agent/issues/740)) ([f2e2fd3](https://github.com/hyperledger-identus/cloud-agent/commit/f2e2fd3d0397422be40b11644f8b84ddd3c6985f))
* **prism-agent:** implement AnonCreds issuance flow ([#693](https://github.com/hyperledger-identus/cloud-agent/issues/693)) ([9165a6f](https://github.com/hyperledger-identus/cloud-agent/commit/9165a6f8fc0a11bd6c19b0bfd4dd4217ea3194d9))

### Bug Fixes

* change repository and name for rest api clients ([#745](https://github.com/hyperledger-identus/cloud-agent/issues/745)) ([0f84e28](https://github.com/hyperledger-identus/cloud-agent/commit/0f84e28c3f2800c1d73353e40620a840fbb6b93a))
* improve performance for background jobs in multitenancy mode ([#749](https://github.com/hyperledger-identus/cloud-agent/issues/749)) ([17def3f](https://github.com/hyperledger-identus/cloud-agent/commit/17def3f67c1eb687560aee844aba6ff2a0bd4137))
* **prism-agent:** agent should read DIDComm port from config ([#757](https://github.com/hyperledger-identus/cloud-agent/issues/757)) ([cda908c](https://github.com/hyperledger-identus/cloud-agent/commit/cda908c87cee562e6c044aa405aa82bd510cc74e))
* **prism-agent:** configure APISIX to return CORS headers from Prism Agent endpoints ([#746](https://github.com/hyperledger-identus/cloud-agent/issues/746)) ([a579aa9](https://github.com/hyperledger-identus/cloud-agent/commit/a579aa95ea5c0c4950cb64b8b9adb1f56bb87eb2))
* **prism-agent:** fix docker env variables interpolation issue ([#751](https://github.com/hyperledger-identus/cloud-agent/issues/751)) ([110eb2d](https://github.com/hyperledger-identus/cloud-agent/commit/110eb2df9590412b35997152d526c599edb8e7af))
* **prism-agent:** return relevant errors on offer creation ([#754](https://github.com/hyperledger-identus/cloud-agent/issues/754)) ([d36533f](https://github.com/hyperledger-identus/cloud-agent/commit/d36533fe538812c9e3647bcc2383700173e4b1b7))
* prohibit tenants to use equal or revoked api keys ([#742](https://github.com/hyperledger-identus/cloud-agent/issues/742)) ([4b10c3a](https://github.com/hyperledger-identus/cloud-agent/commit/4b10c3af931722a683bf55062297c3dfa1e38046))
* upgrade vault and quill versions ([#739](https://github.com/hyperledger-identus/cloud-agent/issues/739)) ([c140857](https://github.com/hyperledger-identus/cloud-agent/commit/c140857df97d56ab750ec186962e5fe2bb6a6717))
## [1.16.4](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.16.3...prism-agent-v1.16.4) (2023-09-29)

### Bug Fixes

* Integration flow ATL-5777 ([#738](https://github.com/hyperledger-identus/cloud-agent/issues/738)) ([7cf927c](https://github.com/hyperledger-identus/cloud-agent/commit/7cf927cc267460cc1708e424c0a63ae96689a42a))
## [1.16.3](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.16.2...prism-agent-v1.16.3) (2023-09-28)

### Bug Fixes

* Adding labels [skip ci] ([#737](https://github.com/hyperledger-identus/cloud-agent/issues/737)) ([5182098](https://github.com/hyperledger-identus/cloud-agent/commit/5182098f7bc6db479ee64d7133389732a38d174a))
## [1.16.2](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.16.1...prism-agent-v1.16.2) (2023-09-28)

### Bug Fixes

* Changing yq command [skip ci] ATL-5777 ([#736](https://github.com/hyperledger-identus/cloud-agent/issues/736)) ([01bdfa7](https://github.com/hyperledger-identus/cloud-agent/commit/01bdfa74056d983bc1fd494c99b1bab8496dc62f))
* Integration flow ([#734](https://github.com/hyperledger-identus/cloud-agent/issues/734)) ([c45a9eb](https://github.com/hyperledger-identus/cloud-agent/commit/c45a9ebf0150245dfa5ebdc2eda94aa1e0fea8f3))
* Renaming values.yml [skip ci] ATL-5777  ([#735](https://github.com/hyperledger-identus/cloud-agent/issues/735)) ([bcd73c3](https://github.com/hyperledger-identus/cloud-agent/commit/bcd73c310a1c033400f83cfa266cbe0aa304217a))
## [1.16.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.16.0...prism-agent-v1.16.1) (2023-09-27)

### Bug Fixes

* Adding localhost environment variable in run.sh script for local development ([#728](https://github.com/hyperledger-identus/cloud-agent/issues/728)) ([1a904a6](https://github.com/hyperledger-identus/cloud-agent/commit/1a904a6f72676fb89f87dd2da14c01d291371f8c))
* correct typo on sts header (dmax -> max) ([#726](https://github.com/hyperledger-identus/cloud-agent/issues/726)) ([2c5bc51](https://github.com/hyperledger-identus/cloud-agent/commit/2c5bc51fc66b2c62a7c8ba7e25944704c335253f))
* **prism-agent:** introduce generic secret store for CD ([#727](https://github.com/hyperledger-identus/cloud-agent/issues/727)) ([3d4aacd](https://github.com/hyperledger-identus/cloud-agent/commit/3d4aacdd9a7f66f2f656d3c31b3f8202cc37c51b))
* Separate config for integration flow ATL-5777 ([#731](https://github.com/hyperledger-identus/cloud-agent/issues/731)) ([9e0e2de](https://github.com/hyperledger-identus/cloud-agent/commit/9e0e2de77a25166f019f78356b2e98b60da7b3e1))
* Separate config for integration flow ATL-5777 ([#733](https://github.com/hyperledger-identus/cloud-agent/issues/733)) ([8380ccc](https://github.com/hyperledger-identus/cloud-agent/commit/8380cccea0eee17c090928b1ae36b877a822177d))
## [1.16.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.15.0...prism-agent-v1.16.0) (2023-09-15)

### Features

* **prism-agent:** Metrics for verification flow ([#714](https://github.com/hyperledger-identus/cloud-agent/issues/714)) ([8bea26e](https://github.com/hyperledger-identus/cloud-agent/commit/8bea26e955987e1543984e090bedad17a7863268))

### Bug Fixes

* change attribute for appuser to login ([#721](https://github.com/hyperledger-identus/cloud-agent/issues/721)) ([a0e0a74](https://github.com/hyperledger-identus/cloud-agent/commit/a0e0a7412172a7cc2010c39c8ee106319e710986))
* entity create and update operation failures if the walletId does… ([#718](https://github.com/hyperledger-identus/cloud-agent/issues/718)) ([4fe6677](https://github.com/hyperledger-identus/cloud-agent/commit/4fe66773a5aad4dc2808dad036c54c4660b3a855))
* **prism-agent:** define db app user privileges before app starts ([#722](https://github.com/hyperledger-identus/cloud-agent/issues/722)) ([8039654](https://github.com/hyperledger-identus/cloud-agent/commit/803965482e2634d488d2f4f364b041917be514a5))
* **prism-agent:** incorrect present proof metric name and remove connectionID from flow metrics ([#720](https://github.com/hyperledger-identus/cloud-agent/issues/720)) ([52e31b0](https://github.com/hyperledger-identus/cloud-agent/commit/52e31b0721d959fa53c8c49a39288b7c50d4582d))
* **prism-agent:** refine multi-tenant error response and validations ([#719](https://github.com/hyperledger-identus/cloud-agent/issues/719)) ([1f9ede3](https://github.com/hyperledger-identus/cloud-agent/commit/1f9ede395c4469bf26b167a6430ad42ea7cde301))
* **prism-agent:** validate application config during startup ([#712](https://github.com/hyperledger-identus/cloud-agent/issues/712)) ([46fd69b](https://github.com/hyperledger-identus/cloud-agent/commit/46fd69bc2416c72dd457b29f06dd181cf65f52a0))
* use postgres application user ([#717](https://github.com/hyperledger-identus/cloud-agent/issues/717)) ([63403a5](https://github.com/hyperledger-identus/cloud-agent/commit/63403a5d64860d4683ebaab00a86eec0578a21c0))
## [1.15.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.14.2...prism-agent-v1.15.0) (2023-09-12)

### Features

* add security headers in helm-chart apisix route ([#697](https://github.com/hyperledger-identus/cloud-agent/issues/697)) ([7f7e0a4](https://github.com/hyperledger-identus/cloud-agent/commit/7f7e0a4b7709c9eb0dbfc0557ed68648a98e5756))
* **prism-agent:** add multi-tenancy capability ([#696](https://github.com/hyperledger-identus/cloud-agent/issues/696)) ([b6c9a40](https://github.com/hyperledger-identus/cloud-agent/commit/b6c9a40733af1a80c2fc7c17650d1f9ca53c21da))
## [1.14.2](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.14.1...prism-agent-v1.14.2) (2023-09-06)

### Bug Fixes

* add missing leading '/' in rewrite rule target ([#692](https://github.com/hyperledger-identus/cloud-agent/issues/692)) ([f2be228](https://github.com/hyperledger-identus/cloud-agent/commit/f2be22895c893525b50a0848d2951e668c5fe688))
## [1.14.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.14.0...prism-agent-v1.14.1) (2023-09-06)

### Bug Fixes

* APISIX config route and rule names too long ([#691](https://github.com/hyperledger-identus/cloud-agent/issues/691)) ([bef008e](https://github.com/hyperledger-identus/cloud-agent/commit/bef008ecb8f07a7e92cba1ba009a343af7a71adb))
## [1.14.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.13.0...prism-agent-v1.14.0) (2023-09-06)

### Features

* **prism-agent:** add syncwave to certificate to fix race condition with tls ([#686](https://github.com/hyperledger-identus/cloud-agent/issues/686)) ([854dcf9](https://github.com/hyperledger-identus/cloud-agent/commit/854dcf96c48defcb1f44062c3dfd88555dcaebe1))

### Bug Fixes

* change private sdk 1.4 to public maven ([#685](https://github.com/hyperledger-identus/cloud-agent/issues/685)) ([128bcac](https://github.com/hyperledger-identus/cloud-agent/commit/128bcac5b7006b485ea3dc9272fde29d159f1a03))
* **prism-agent:** update invitation expiration on connection request ([#687](https://github.com/hyperledger-identus/cloud-agent/issues/687)) ([1a1702f](https://github.com/hyperledger-identus/cloud-agent/commit/1a1702fc4e62b4a03a4e4ee32ac7419ea67a4ea1))
## [1.13.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.12.0...prism-agent-v1.13.0) (2023-09-06)

### Features

* **prism-agent:** metrics for issuance flow ([#669](https://github.com/hyperledger-identus/cloud-agent/issues/669)) ([20315ae](https://github.com/hyperledger-identus/cloud-agent/commit/20315aedea3c8c2953cfd5ee391feb10fbc1146c))

### Bug Fixes

* another indentation in apisixroute ([#683](https://github.com/hyperledger-identus/cloud-agent/issues/683)) ([d7c5e52](https://github.com/hyperledger-identus/cloud-agent/commit/d7c5e526c2b40897b3e26a8272468f21fc1dd81f))
* correct vault path ([#678](https://github.com/hyperledger-identus/cloud-agent/issues/678)) ([9426e7f](https://github.com/hyperledger-identus/cloud-agent/commit/9426e7f069e2dd72c18b1e09f7b34b2d37854771))
* CredentialOffer not following spec ([#569](https://github.com/hyperledger-identus/cloud-agent/issues/569)) ([3d479b9](https://github.com/hyperledger-identus/cloud-agent/commit/3d479b9fc0c0bdb0aa78b8c4e2edc8d287a0b6d9))
* indentation in apisixroute ([#682](https://github.com/hyperledger-identus/cloud-agent/issues/682)) ([6eec8ba](https://github.com/hyperledger-identus/cloud-agent/commit/6eec8ba6a32ce088eaaeafe953c4ff554d4765ab))
* **prism-agent:** fix added the InvitationGenerated state to the conn… ([#684](https://github.com/hyperledger-identus/cloud-agent/issues/684)) ([7fdffe3](https://github.com/hyperledger-identus/cloud-agent/commit/7fdffe3990ea08bd6dad5f9d2124146cb8efcff4))
* **prism-agent:** make resolve did representation content type work ([#679](https://github.com/hyperledger-identus/cloud-agent/issues/679)) ([fd417d9](https://github.com/hyperledger-identus/cloud-agent/commit/fd417d9bdac0db98bc3de7a84e4d3277aef3c403))
## [1.12.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.11.0...prism-agent-v1.12.0) (2023-08-31)

### Features

* add anoncreds credential definition rest api ([#624](https://github.com/hyperledger-identus/cloud-agent/issues/624)) ([99e338a](https://github.com/hyperledger-identus/cloud-agent/commit/99e338af6dc1ab2b4b42f4b1bee2a917ccb77b4c))
* allow external API keys to be defined for an agent ([#643](https://github.com/hyperledger-identus/cloud-agent/issues/643)) ([756dea7](https://github.com/hyperledger-identus/cloud-agent/commit/756dea707b1ced9de800cdabfded6dfc100e340e))
* ATL-5571 Generalized Vault to Store Json ([#650](https://github.com/hyperledger-identus/cloud-agent/issues/650)) ([ebf0328](https://github.com/hyperledger-identus/cloud-agent/commit/ebf0328cfb5107954766fe93ffc6b42f4e5a4cb0))
* ATL-5574 Prime Anoncred Lib ([#652](https://github.com/hyperledger-identus/cloud-agent/issues/652)) ([70b2f16](https://github.com/hyperledger-identus/cloud-agent/commit/70b2f16beecdef7eeeabb18f1b25244046ba5a65))
* ATL-5575 Generalize and Streamline Json Schema SerDes logic ([#653](https://github.com/hyperledger-identus/cloud-agent/issues/653)) ([eb4f8f4](https://github.com/hyperledger-identus/cloud-agent/commit/eb4f8f488bcef421e20f770669dfff99f4c1dd98))

### Bug Fixes

* **prism-agent:** invitation expiry configuration and new state ([#655](https://github.com/hyperledger-identus/cloud-agent/issues/655)) ([c61999d](https://github.com/hyperledger-identus/cloud-agent/commit/c61999dd2a256401c30d29b842f0092f4968c6ed))
## [1.11.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.10.0...prism-agent-v1.11.0) (2023-08-21)

### Features

* **prism-agent:** Add prism agent record processing pipeline parameters ([#626](https://github.com/hyperledger-identus/cloud-agent/issues/626)) ([434bdac](https://github.com/hyperledger-identus/cloud-agent/commit/434bdacfc10b854b77bde0c8c7add613d8ee9025))

### Bug Fixes

* **prism-agenet:** Remove connection ID from metrics in connection flow ([#635](https://github.com/hyperledger-identus/cloud-agent/issues/635)) ([515f92f](https://github.com/hyperledger-identus/cloud-agent/commit/515f92f67f6ccd9ae5414d1324ebb769c43d5017))
## [1.10.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.9.2...prism-agent-v1.10.0) (2023-08-16)

### Features

* **agent:** improve OAS spec and refactor DidCommHttpServer code ([#615](https://github.com/hyperledger-identus/cloud-agent/issues/615)) ([301fbab](https://github.com/hyperledger-identus/cloud-agent/commit/301fbabac6c743130c46572056d9b8848a166be1))
* **prism-agent:** Metrics for connection flow job ([#611](https://github.com/hyperledger-identus/cloud-agent/issues/611)) ([695d661](https://github.com/hyperledger-identus/cloud-agent/commit/695d66173b40b3ee9f87c3b950b54bdeff8f02d2))
* update anoncreds demo after the new getJson methods ([#584](https://github.com/hyperledger-identus/cloud-agent/issues/584)) ([d8258ee](https://github.com/hyperledger-identus/cloud-agent/commit/d8258ee5d29b94ac863f9dbf5c8eaadd66fd636e))

### Bug Fixes

* **prism-agent:** fix OAS on empty DID resolution representation ([#616](https://github.com/hyperledger-identus/cloud-agent/issues/616)) ([216ff3a](https://github.com/hyperledger-identus/cloud-agent/commit/216ff3a2ef75d824d0a6285218be01636a595a82))

### Performance Improvements

* support for credential and present-proof flows with thid ([#609](https://github.com/hyperledger-identus/cloud-agent/issues/609)) ([9cef8c0](https://github.com/hyperledger-identus/cloud-agent/commit/9cef8c03cf0a3e5601ec36b1f008dea2a738a415))
## [1.9.2](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.9.1...prism-agent-v1.9.2) (2023-07-27)

### Bug Fixes

* consumer variable nesting correction ([#606](https://github.com/hyperledger-identus/cloud-agent/issues/606)) ([40a0578](https://github.com/hyperledger-identus/cloud-agent/commit/40a0578274d33873c5189d01715244b2b34c0fea))
## [1.9.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.9.0...prism-agent-v1.9.1) (2023-07-25)

### Bug Fixes

* include helm Chart.yaml in git commit for release process ([#604](https://github.com/hyperledger-identus/cloud-agent/issues/604)) ([d0372f1](https://github.com/hyperledger-identus/cloud-agent/commit/d0372f19e74ade5627a41038b07010321d5ef600))
## [1.9.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.8.0...prism-agent-v1.9.0) (2023-07-25)

### Features

* add helm-chart for agent  ([#603](https://github.com/hyperledger-identus/cloud-agent/issues/603)) ([63f38d4](https://github.com/hyperledger-identus/cloud-agent/commit/63f38d47f4645bf6172320da5c3413c748c03729))
## [1.8.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.7.0...prism-agent-v1.8.0) (2023-07-20)

### Features

* ATL-4888 Anoncred schema type ([#590](https://github.com/hyperledger-identus/cloud-agent/issues/590)) ([a57deef](https://github.com/hyperledger-identus/cloud-agent/commit/a57deef485fea5181e8617a30ab70ca26c409b42))

### Bug Fixes

* **castor:** align DID document translation logic ([#595](https://github.com/hyperledger-identus/cloud-agent/issues/595)) ([bb1f112](https://github.com/hyperledger-identus/cloud-agent/commit/bb1f1121975c3bc8288b1d4577efd3922e5adce7))
* **prism-agent:** add did-method path segment in HD key derivation ([#596](https://github.com/hyperledger-identus/cloud-agent/issues/596)) ([a1e457a](https://github.com/hyperledger-identus/cloud-agent/commit/a1e457a8d6337e8c941b58c802f9516fe6718396))
## [1.7.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.6.0...prism-agent-v1.7.0) (2023-07-10)

### Features

* **prism-agent:** add http metrics ([#585](https://github.com/hyperledger-identus/cloud-agent/issues/585)) ([f62d7f5](https://github.com/hyperledger-identus/cloud-agent/commit/f62d7f5459f12f93224b0eb9b05caf605f54be2c))
* **prism-agent:** align DID document service handling with the spec ([#582](https://github.com/hyperledger-identus/cloud-agent/issues/582)) ([c9e69f6](https://github.com/hyperledger-identus/cloud-agent/commit/c9e69f602ef5e78848ad6d652f0ba7d4d4d2db2d))
* **prism-agent:** expose connect/issue/presentation records 'thid' and add it to REST API queries ([#583](https://github.com/hyperledger-identus/cloud-agent/issues/583)) ([9a97c7a](https://github.com/hyperledger-identus/cloud-agent/commit/9a97c7a6e5815fd80eba0f98042a49356fc1f61c))
* **prism-agent:** simple event mechanism using webhook ([#575](https://github.com/hyperledger-identus/cloud-agent/issues/575)) ([42cf8c9](https://github.com/hyperledger-identus/cloud-agent/commit/42cf8c9b47b2ac2d17e6d00b0901806e0f0e2e1d)), closes [#1](https://github.com/hyperledger-identus/cloud-agent/issues/1)

### Bug Fixes

* Adding Apollo ADR ([#573](https://github.com/hyperledger-identus/cloud-agent/issues/573)) ([e036bc8](https://github.com/hyperledger-identus/cloud-agent/commit/e036bc84446c8b7eb008f536def2adaca08e071f))
* **castor:** fix DID parser that failing to parse some DIDs ([#581](https://github.com/hyperledger-identus/cloud-agent/issues/581)) ([24b2300](https://github.com/hyperledger-identus/cloud-agent/commit/24b230023ad2812dc13aa7229163ead5eb56183d))
* **pollux:** add pagination at db level for getCredentialRecords ([#586](https://github.com/hyperledger-identus/cloud-agent/issues/586)) ([c0db5c8](https://github.com/hyperledger-identus/cloud-agent/commit/c0db5c8a2a4fee7568fb5aa43f81a2faba6936a2))

### Performance Improvements

* add k6 connection flow running in CI ([#572](https://github.com/hyperledger-identus/cloud-agent/issues/572)) ([601f934](https://github.com/hyperledger-identus/cloud-agent/commit/601f934062537c8080657b6268299f18d8201ec2))
## [1.6.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.5.1...prism-agent-v1.6.0) (2023-06-28)

### Features

* new Anoncreds Demo ([#562](https://github.com/hyperledger-identus/cloud-agent/issues/562)) ([a9a8290](https://github.com/hyperledger-identus/cloud-agent/commit/a9a8290c73fb3044c2091311c199d1e532af03f0))

### Bug Fixes

* **prism-agent:** decouple secret storage backend from agent ([#570](https://github.com/hyperledger-identus/cloud-agent/issues/570)) ([6a5f9ce](https://github.com/hyperledger-identus/cloud-agent/commit/6a5f9cef337848dadd8a54b54948db9e7edfe8ad))
* **prism-agent:** fix concurrent requests breaking DID index counter ([#571](https://github.com/hyperledger-identus/cloud-agent/issues/571)) ([e8411dd](https://github.com/hyperledger-identus/cloud-agent/commit/e8411ddb588e9dc81f2437cfbdfdcd1be42f99d1))
* **prism-agent:** use correct pairwise DIDs in presentation flow ([#568](https://github.com/hyperledger-identus/cloud-agent/issues/568)) ([ede234b](https://github.com/hyperledger-identus/cloud-agent/commit/ede234bbdcb64cb48da182b374288b549b8cf8aa))
## [1.5.1](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.5.0...prism-agent-v1.5.1) (2023-06-22)

### Bug Fixes

* log seed error before effect fail ([#557](https://github.com/hyperledger-identus/cloud-agent/issues/557)) ([c3a5d8e](https://github.com/hyperledger-identus/cloud-agent/commit/c3a5d8eb9e62675053f9b7fc80ee18d7a62f857c))
## [1.5.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.4.0...prism-agent-v1.5.0) (2023-06-16)

### Features

* **prism-agent:** integrate credential schema into VC issue flow ([#541](https://github.com/hyperledger-identus/cloud-agent/issues/541)) ([ab88736](https://github.com/hyperledger-identus/cloud-agent/commit/ab88736dc9d6dfec3d39f6a58619eb115f520bf8))
* **prism-agent:** integrate DID secret storage with Vault ([#543](https://github.com/hyperledger-identus/cloud-agent/issues/543)) ([ee43feb](https://github.com/hyperledger-identus/cloud-agent/commit/ee43febacddb06210065c3f812beb8c948d5c369))
## [1.4.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.3.0...prism-agent-v1.4.0) (2023-06-01)

### Features

* **prism-agent:** add support for hierarchical deterministic key with seed ([#534](https://github.com/hyperledger-identus/cloud-agent/issues/534)) ([6129baf](https://github.com/hyperledger-identus/cloud-agent/commit/6129baf1210b68decc4f264bd4a64b4009719956))

### Bug Fixes

* **prism-agent:** infinite loop in proof presentation execution ([#540](https://github.com/hyperledger-identus/cloud-agent/issues/540)) ([6a26bb7](https://github.com/hyperledger-identus/cloud-agent/commit/6a26bb78d256bdcd09918cb1e8ee5bfd5cf0dacc))
## [1.3.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.2.0...prism-agent-v1.3.0) (2023-05-23)

### Features

* restore JVM metrics endpoint capability ([#527](https://github.com/hyperledger-identus/cloud-agent/issues/527)) ([7d603f0](https://github.com/hyperledger-identus/cloud-agent/commit/7d603f09abd6042368ada6afa3685332342d6860))
## [1.2.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.1.0...prism-agent-v1.2.0) (2023-05-17)

### Features

* migrate issue endpoint to tapir ([#516](https://github.com/hyperledger-identus/cloud-agent/issues/516)) ([9b1558f](https://github.com/hyperledger-identus/cloud-agent/commit/9b1558f50003ba1c79ec2cdd9888f2e99f0534d8))
* **prism-agent:** fix infinite reprocessing of records in error ([#528](https://github.com/hyperledger-identus/cloud-agent/issues/528)) ([904a2dc](https://github.com/hyperledger-identus/cloud-agent/commit/904a2dcb09d2e907e284479c652c5f389fd0dec9))
* **prism-agent:** migrate present-proof endpoints to Tapir ([#525](https://github.com/hyperledger-identus/cloud-agent/issues/525)) ([cb01657](https://github.com/hyperledger-identus/cloud-agent/commit/cb016570b6d0a1b0de98928d6daa1cbf055d26b4))

### Bug Fixes

* **prism-agent:** refactor crypto abstraction in the walletAPI ([#522](https://github.com/hyperledger-identus/cloud-agent/issues/522)) ([e36c634](https://github.com/hyperledger-identus/cloud-agent/commit/e36c63424ed2e28fc360c6a6a5d557938d4ec01a))
## [1.1.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v1.0.0...prism-agent-v1.1.0) (2023-05-05)

### Features

* add multi-arch amd64 and arm64 support for agent docker ([#512](https://github.com/hyperledger-identus/cloud-agent/issues/512)) ([dc2608c](https://github.com/hyperledger-identus/cloud-agent/commit/dc2608c12e062a6af5d3fcf1077956281a2f0828))
* expose API for default did:peer of prism agent ([#509](https://github.com/hyperledger-identus/cloud-agent/issues/509)) ([b128292](https://github.com/hyperledger-identus/cloud-agent/commit/b128292031c547938614b21fb4cd0a377310c19e))
* **prism-agent:** migrate DID endpoint to tapir ([#511](https://github.com/hyperledger-identus/cloud-agent/issues/511)) ([9d587ff](https://github.com/hyperledger-identus/cloud-agent/commit/9d587ffc6e44da9dacb0af76e922030828831805))
* **prism-agent:** migrate did-registrar endpoint to tapir ([#517](https://github.com/hyperledger-identus/cloud-agent/issues/517)) ([88eeefd](https://github.com/hyperledger-identus/cloud-agent/commit/88eeefdad81e05197ea0b6c2bf449c4c2960e023))

### Bug Fixes

* **pollux:** ATL-3971 Fix all Deprecation Warning ([#506](https://github.com/hyperledger-identus/cloud-agent/issues/506)) ([e5225b7](https://github.com/hyperledger-identus/cloud-agent/commit/e5225b7101bf3572a85a6f0cf8ed05e93410f551))
## [1.0.0](https://github.com/hyperledger-identus/cloud-agent/compare/prism-agent-v0.60.2...prism-agent-v1.0.0) (2023-04-10)

### ⚠ BREAKING CHANGES

* incrementing major version for the first stable release

### Features

* add prism-agent-* prefix to the tag ([#505](https://github.com/hyperledger-identus/cloud-agent/issues/505)) ([6087f2d](https://github.com/hyperledger-identus/cloud-agent/commit/6087f2dcc77179a4bb4702e60a9669c6329ba55c))
* **castor:** add support for context in DID document & operation ([#489](https://github.com/hyperledger-identus/cloud-agent/issues/489)) ([8384fe3](https://github.com/hyperledger-identus/cloud-agent/commit/8384fe39be38f24e6b821851781c6b465e8e4bfa))
* **prism-agent:** migrate connect endpoints to Tapir ([#493](https://github.com/hyperledger-identus/cloud-agent/issues/493)) ([876dd9e](https://github.com/hyperledger-identus/cloud-agent/commit/876dd9ed4b89f7c2cf779d47bb89b8a0358743db))
* **prism-node:** add context to protobuf definition ([#487](https://github.com/hyperledger-identus/cloud-agent/issues/487)) ([e426a82](https://github.com/hyperledger-identus/cloud-agent/commit/e426a82b4f593204f1dc69c2b65c7362e8707ec6))
* Reply to Trust Pings ([#496](https://github.com/hyperledger-identus/cloud-agent/issues/496)) ([b07da78](https://github.com/hyperledger-identus/cloud-agent/commit/b07da78d3ee927c7ddfbd311d442a687b0b901a4))
