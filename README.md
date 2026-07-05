# PbmPersistenceCommons

Shared persistence and transport library for the Clash of Legends Java projects
(Counselor and the server-side pipeline). It holds the settings/bundle managers,
the XStream-based EGF serialization (`XmlManager`/`ZipManager`), the HTTP upload
client (`WebCounselorManager`), and shared utilities (`SysApoio`, `SysProperties`).

It has a one-way relationship with `PbmCommons`: `PbmCommons` depends on the
compiled `PbmPersistenceCommons.jar`; this library has zero imports from
`PbmCommons`.

## How to build

**Prerequisites**
- JDK 21 (tested with Temurin 21.0.11)
- Apache Ant 1.10+ (NetBeans build) or Apache Maven 3.9+

**Ant**
```
ant jar
```
Output lands in `dist/PbmPersistenceCommons.jar`.

**Maven**
```
mvn clean package
```

**Note on JDK 21 + XStream:** XStream 1.4.21 needs several `--add-opens` flags at
runtime under Java 21. They are provided via `.mvn/jvm.config` for Maven runs; the
consuming applications set the same flags in their launchers.

## License

PbmPersistenceCommons is released under the [MIT License](LICENSE). © 2004-2026 Clash of Legends.
