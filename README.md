# LTI Gradebook

La presente herramienta es un libro de calificaciones que se integra en Canvas via LTI desarrollado para la Pontificia Universidad Católica de Chile por parte de [Entornos de Formación](https://www.edf.global).

La herramienta se apoya en la librería `lti-launch` desarrollada por Kansas State, en su variante utilizada por Oxford University. También se apoya en la librería `canvas-api` desarrollada por Kansas State con algunos añadidos por parte de [Entornos de Formación](https://www.edf.global), actualmente se transicionó a la variante de Oxford University.

## Requisitos
- Maven 3.8.2 o superior
- Java 11
- Oracle 12c, usuario con permisos para generar y actualizar tablas en un esquema.

## Compilación
Para compilar la herramienta debemos primero instalar la librería de Oracle que se distribuye por separado, en el código fuente viene una copia de dicha librería.
```bash
mvn install:install-file -Dfile=lib/ojdbc8-19.3.jar -DgroupId=com.oracle.jdbc -DartifactId=ojdbc8 -Dversion=19.3.0.0 -Dpackaging=jar
```
Una vez instalada la librería, podemos compilar la aplicación sin problemas.
```bash
mvn clean install -DskipTests
```
Recomendamos deshabilitar los tests si la aplicación no está correctamente configurada.

## Configuración
La aplicación debe configurarse utilizando el fichero de ejemplo "application.properties", situado en la carpeta src\main\resources. 

Recomendamos alojar dicho fichero fuera de la aplicación por cuestiones de seguridad.

Por defecto Spring Boot escanea la carpeta config por lo que alojar el fichero en la ruta 'config/application.properties' es una buena solución.

Las propiedades más importantes a configurar son:

|Propiedad| Uso| Defecto |Ejemplo|
|---|---|---|---|
| lti-gradebook.instance | Usuario de LTI que se configura en Canvas, shared key. | canvas | canvas  | 
| lti-gradebook.secret | Clave secreta de LTI que se configura en Canvas, shared secret. | canvas | canvas  |
| lti-gradebook.name | Nombre de la aplicación LTI que se configura en Canvas | LTI Gradebook | Libro de notas UC |
| lti-gradebook.url | URL de la instancia de Canvas donde se conecta la aplicación | https://cursos.canvas.uc.cl | https://cursos.canvas.uc.cl |
| spring.datasource.username | Usuario de la base de datos | - | - |
| spring.datasource.password | Password del usuario de la base de datos | - | - |
| datasource | SID de la base de datos Oracle | - | ORCL |
| hibernate.dialect | Dialecto de Hibernate que se va a utilizar | org.hibernate.dialect.Oracle12cDialect | org.hibernate.dialect.Oracle12cDialect |
| spring.datasource.url | Cadena de conexión a la base de datos incluyendo SID y puerto | - | jdbc:oracle:thin:@localhost:1521:ORCL |
| server.port | Puerto donde se ejecutará la aplicación | 8080 | 8443 |
| security.require-ssl| Habilitar modo seguro de la aplicación (HTTPS) | false | true |
| server.ssl.key-store-type | Tipo de certificado a utilizar en modo seguro | - | PKCS12  |
| server.ssl.key-store | Ruta del fichero de almacén de claves para ficheros autogenerados. | - | /home/user/keystore.p12 |
| server.ssl.key-store-password | Password del almacén de claves | - | changeit |
| lti-gradebook.canvas_api_token | Token de API que utilizará la aplicación para realizar llamadas cuando no encuentre ningún token en la base de datos. | - | - |
| lti-gradebook.admins | Listado de usuarios de Canvas que son administradores de la aplicación, separados por comas. | - | mpellicer,jdoe,hsolo |
| banner.enabled | Habilitar la integración con Banner. | false | false |
| banner.datasource.username | Nombre de usuario de la conexión al esquema de Banner. | - | WEB_BANNER_LMS |
| banner.datasource.password | Password del usuario de la conexión al esquema de Banner. | - | - |
| banner.datasource.driver-class-name | Drivers de conexión al esquema de Banner. | oracle.jdbc.driver.OracleDriver | oracle.jdbc.driver.OracleDriver |
| banner.datasource.url | URL de conexión al esquema de Banner. | - | jdbc:oracle:thin:@localhost:1521:ORCL |
| banner.datasource.connectionInitSql | SQL de inicialización. | - | SET ROLE rol_banner_lms_carga_notas identified by rlms_carga1911 |
| logging.level.edu.uc.ltigradebook | Modificar el nivel de logging, habilitar DEBUG para depuración. | INFO | DEBUG |

## Ejecución
Para ejecutar el código necesitamos configurar correctamente la aplicación y lanzar el siguiente comando:

```bash
java -jar target/lti-gradebook.war -Dspring.config.location=/path/to/configuration/application.properties
```
Donde `/path/to/configuration/application.properties` hace referencia a la ruta donde está alojado el fichero application.properties.

A la hora de construir un entorno de desarrollo se puede utilizar la utilidad mkcert y configurar un certificado válido para localhost [MKCERT](https://github.com/FiloSottile/mkcert).

Al usar un certificado mkcert alojado en la carpeta config, estas propiedades serían suficientes:
```
server.port=8443
server.ssl.key-store=config/keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=changeit
```

Con esta configuración también podemos lanzar la aplicación directamente, recomendamos situar la configuración por defecto en config/application.properties.
```bash
mvn clean install -DskipTests spring-boot:run
```

## Instalación en Canvas
----------------------

Para instalar la herramienta en Canvas, modifica el fichero `lti.xml` con la información y rutas públicas de la herramienta, usa ese fichero a la hora de [importar una herramienta LTI en Canvas](https://community.canvaslms.com/docs/DOC-10724-67952720325). 

