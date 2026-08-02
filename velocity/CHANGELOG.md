# Changelog

## [0.14.1](https://github.com/groundsgg/containers/compare/velocity@v0.14.0...velocity@v0.14.1) (2026-08-02)


### Bug Fixes

* **velocity:** bake plugin-agones 0.8.1 — no transfers out of running rounds ([#206](https://github.com/groundsgg/containers/issues/206)) ([511faa5](https://github.com/groundsgg/containers/commit/511faa559c407feb1797b9dc0636325ac4e2b947))

## [0.14.0](https://github.com/groundsgg/containers/compare/velocity@v0.13.0...velocity@v0.14.0) (2026-08-02)


### Features

* **velocity:** bake plugin-agones 0.8.0 ([#204](https://github.com/groundsgg/containers/issues/204)) ([3ea2552](https://github.com/groundsgg/containers/commit/3ea25523155bec891f6e9c7cad19c1e02f1270fe))

## [0.13.0](https://github.com/groundsgg/containers/compare/velocity@v0.12.3...velocity@v0.13.0) (2026-07-27)


### Features

* **velocity:** bundle permissions REST client ([#198](https://github.com/groundsgg/containers/issues/198)) ([74bf138](https://github.com/groundsgg/containers/commit/74bf1380cc8a94d8b57969e1bda105494c51eb78))

## [0.12.3](https://github.com/groundsgg/containers/compare/velocity@v0.12.2...velocity@v0.12.3) (2026-07-25)


### Bug Fixes

* **velocity:** bake plugin-agones 0.7.2 so /agones counts the network ([#195](https://github.com/groundsgg/containers/issues/195)) ([4107794](https://github.com/groundsgg/containers/commit/4107794e968ccab57b9e18d298cf9f0fc4758ff8))
* **velocity:** secure build secret and update permissions plugin ([#197](https://github.com/groundsgg/containers/issues/197)) ([b495464](https://github.com/groundsgg/containers/commit/b495464d4376064647ab82af58edd08e806e00f7))

## [0.12.2](https://github.com/groundsgg/containers/compare/velocity@v0.12.1...velocity@v0.12.2) (2026-07-14)


### Bug Fixes

* **velocity:** bump plugin-permissions to 0.5.0 so permission checks work ([#186](https://github.com/groundsgg/containers/issues/186)) ([b84a0f9](https://github.com/groundsgg/containers/commit/b84a0f940f57515ee8aeab5be7deed3755c572c9))

## [0.12.1](https://github.com/groundsgg/containers/compare/velocity@v0.12.0...velocity@v0.12.1) (2026-07-13)


### Bug Fixes

* **velocity:** plugin-agones 0.7.1 — the proxy can find a pushed gamemode ([#184](https://github.com/groundsgg/containers/issues/184)) ([d293a1f](https://github.com/groundsgg/containers/commit/d293a1fd6012f52fe92bfe3948c232c9a699e201))

## [0.12.0](https://github.com/groundsgg/containers/compare/velocity@v0.11.0...velocity@v0.12.0) (2026-07-13)


### Features

* **velocity:** bake plugin-permissions into the proxy image ([#182](https://github.com/groundsgg/containers/issues/182)) ([d21d688](https://github.com/groundsgg/containers/commit/d21d688925f86400ee9a29734b3cb531c68dd095))

## [0.11.0](https://github.com/groundsgg/containers/compare/velocity@v0.10.0...velocity@v0.11.0) (2026-07-12)


### Features

* **velocity:** platform plugin 0.6.0 (server icon) ([#179](https://github.com/groundsgg/containers/issues/179)) ([541ea18](https://github.com/groundsgg/containers/commit/541ea18589358ed1d5e55acd5b026f1334950d0b))


### Bug Fixes

* **velocity:** upgrade the proxy to 3.5.1 for Minecraft 26.2 ([#181](https://github.com/groundsgg/containers/issues/181)) ([da311f0](https://github.com/groundsgg/containers/commit/da311f08c7f1ab13b067dfe20737d81d2fbafccb))

## [0.10.0](https://github.com/groundsgg/containers/compare/velocity@v0.9.2...velocity@v0.10.0) (2026-06-05)


### Features

* **velocity:** bake grounds-platform velocity plugin (whitelist + MOTD) ([#155](https://github.com/groundsgg/containers/issues/155)) ([4fb0a49](https://github.com/groundsgg/containers/commit/4fb0a49bc095568396975fa52835415a3c634c5b))


### Bug Fixes

* update platform plugin to 0.5.0 ([#160](https://github.com/groundsgg/containers/issues/160)) ([3defd01](https://github.com/groundsgg/containers/commit/3defd01e5edcee58b7b6cf766109b47808dc5053))
* **velocity:** bump grounds-platform plugin to 0.4.1 ([#158](https://github.com/groundsgg/containers/issues/158)) ([c4a25fd](https://github.com/groundsgg/containers/commit/c4a25fdc6507a9bd88cac8bde0e5bd9707f8777f))
* **velocity:** bump plugin-agones-velocity to 0.5.1 ([#157](https://github.com/groundsgg/containers/issues/157)) ([5657d37](https://github.com/groundsgg/containers/commit/5657d374ad8349ba3dfd4ca9f7ad5f7c90c8c079))

## [0.9.2](https://github.com/groundsgg/containers/compare/velocity@v0.9.1...velocity@v0.9.2) (2026-06-04)


### Bug Fixes

* **velocity:** bump Velocity to 3.5.0-SNAPSHOT (601) for MC 26.1.2 support ([#153](https://github.com/groundsgg/containers/issues/153)) ([322ff06](https://github.com/groundsgg/containers/commit/322ff06472e44306c41e5754641eabed0bcb89c5))

## [0.9.1](https://github.com/groundsgg/containers/compare/velocity@v0.9.0...velocity@v0.9.1) (2026-06-04)


### Bug Fixes

* **velocity:** materialize forwarding secret file at boot ([#147](https://github.com/groundsgg/containers/issues/147)) ([207798d](https://github.com/groundsgg/containers/commit/207798dcdec119d0abd9da8453689b438b2dd31d))

## [0.9.0](https://github.com/groundsgg/containers/compare/velocity@v0.8.0...velocity@v0.9.0) (2026-05-19)


### Features

* install grounds runtime from platform packages ([#133](https://github.com/groundsgg/containers/issues/133)) ([0117ee9](https://github.com/groundsgg/containers/commit/0117ee906b61422ccada1e64d98d7c52915df0a7))

## [0.8.0](https://github.com/groundsgg/containers/compare/velocity@v0.7.0...velocity@v0.8.0) (2026-05-12)


### Features

* update agones plugin ([#130](https://github.com/groundsgg/containers/issues/130)) ([6af7e4e](https://github.com/groundsgg/containers/commit/6af7e4ef0253e72bb189e061fc17a1fd69fd04b8))

## [0.7.0](https://github.com/groundsgg/containers/compare/velocity@v0.6.0...velocity@v0.7.0) (2026-05-12)


### Features

* **paper:** release without bundled plugin-agones-paper ([41b6d66](https://github.com/groundsgg/containers/commit/41b6d66e14f837b11486a709c93f5d8126327de6))

## [0.6.0](https://github.com/groundsgg/containers/compare/velocity@v0.5.0...velocity@v0.6.0) (2026-01-04)


### Features

* enable accepts-transfers in velocity ([#41](https://github.com/groundsgg/containers/issues/41)) ([5449da1](https://github.com/groundsgg/containers/commit/5449da14dbff77ea1669f224d45d405cc9595828))

## [0.5.0](https://github.com/groundsgg/containers/compare/velocity@v0.4.0...velocity@v0.5.0) (2026-01-04)


### Features

* switch plugin-server-discovery to plugin-agones in paper and velocity ([1d8d806](https://github.com/groundsgg/containers/commit/1d8d80647b212a82183ca742eb338e100b00394a))

## [0.4.0](https://github.com/groundsgg/containers/compare/velocity@v0.3.0...velocity@v0.4.0) (2026-01-02)


### Features

* add dynamic plugin installation ([#34](https://github.com/groundsgg/containers/issues/34)) ([299b704](https://github.com/groundsgg/containers/commit/299b704e6aacdb5df7eec9a017036017a6b8a9aa))

## [0.3.0](https://github.com/groundsgg/containers/compare/velocity@v0.2.2...velocity@v0.3.0) (2025-12-25)


### Features

* remove forwarding.secret.example and update README for environment variable usage ([e720f81](https://github.com/groundsgg/containers/commit/e720f81fcf39964dbdce86100156f1e4537ce763))


### Bug Fixes

* document the changed values in velocity.toml and revert other changes ([9b81126](https://github.com/groundsgg/containers/commit/9b811269d924645ea9894d990bdf2a0bd7b31b8c))

## [0.2.2](https://github.com/groundsgg/containers/compare/velocity@0.2.1...velocity@v0.2.2) (2025-12-24)


### Bug Fixes

* **velocity:** align startup flags with docs ([#26](https://github.com/groundsgg/containers/issues/26)) ([f449b67](https://github.com/groundsgg/containers/commit/f449b67bdda24b4bdf1bacbaf36c53c4af97c68c))

## [0.2.1](https://github.com/groundsgg/containers/compare/velocity@v0.2.0...velocity@0.2.1) (2025-12-24)


### Bug Fixes

* **velocity:** optimize docker image ([#23](https://github.com/groundsgg/containers/issues/23)) ([5815c1a](https://github.com/groundsgg/containers/commit/5815c1aeded3536183fc12eacf28492a5c58f549))

## [0.2.0](https://github.com/groundsgg/containers/compare/velocity@v0.1.0...velocity@v0.2.0) (2025-12-24)


### Features

* add labels to the velocity Dockerfile ([#18](https://github.com/groundsgg/containers/issues/18)) ([118db60](https://github.com/groundsgg/containers/commit/118db6078ae5ff82025ce0d021b9e473099bb6ce))

## [0.1.0](https://github.com/groundsgg/containers/compare/velocity@v0.0.1...velocity@v0.1.0) (2025-12-23)


### Features

* add velocity image ([#13](https://github.com/groundsgg/containers/issues/13)) ([1caed07](https://github.com/groundsgg/containers/commit/1caed07e382ad8abc1845d7e8e9ebad02742ef48))
