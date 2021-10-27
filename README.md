# LTI Gradebook

La presente herramienta es un libro de calificaciones que se integra en Canvas via LTI desarrollado para la Pontificia Universidad Católica de Chile por parte de [Entornos de Formación](https://www.edf.global).

La herramienta se apoya en la librería `lti-launch` desarrollada por Kansas State, en su variante utilizada por Oxford University. También se apoya en la librería `canvas-api` desarrollada por Kansas State con algunos añadidos por parte de [Entornos de Formación](https://www.edf.global), actualmente se transicionó a la variante de Oxford University.

## Requisitos
- Maven 3.8.2 o superior.
- Java 8 o 11.
- Oracle o MySQL, usuario con permisos para generar y actualizar tablas en un esquema.

## Compilación
Para compilar la herramienta debemos recurrir al siguiente comando de Maven:

```bash
mvn clean install
```

Esto generará un fichero .WAR en la carpeta target que puede ejecutarse directamente (Ver sección de ejecución) o bien desplegarse en un contenedor de servlets como Apache Tomcat.

## Configuración
La aplicación debe configurarse utilizando el fichero de ejemplo "application.properties", situado en la carpeta src\main\resources. 

Recomendamos alojar dicho fichero fuera de la aplicación por cuestiones de seguridad.

Por defecto Spring Boot escanea la carpeta config por lo que alojar el fichero en la ruta 'config/application.properties' es una buena solución como entorno de desarrollo.

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
| sync.grades.enabled | Permite habilitar el trabajo de sincronización de notas explorando los cursos del sistema LTI. | false | false |
| sync.grades.interval | Modificar el intervalo de ejecución de la sincronización de notas con Canvas. | PT12H | PT12H |
| sync.grades.initial.delay | Modificar el retraso inicial de la primera ejecución de la sincronización de notas con Canvas. | PT1S | PT1S |
| cache.expiry.interval | Modificar la duración de la caché del sistema. | PT10M | PT10M |
| cache.initial.delay | Modificar el retraso inicial de la primera limpieza de caché del sistema. | PT1S | PT1S |
| sync.submissions.enabled | Permite habilitar el trabajo de sincronización de entregas basado en la descarga de informes de cuenta. | false | false |
| sync.submissions.interval | Modificar el intervalo de ejecución del trabajo de sincronización de entregas basado en la descarga de informes de cuenta. | PT12H | PT12H |
| sync.submissions.initial.delay | Modificar el retraso inicial de la primera ejecución de sincronización de entregas basado en la descarga de informes de cuenta. | PT1S | PT1S |
| canvas.accountreport.max.duration | Modificar el tiempo máximo que el proceso esperará a un informe de cuenta. | PT3H | PT3H |
| canvas.accountreport.sleep.interval | Modificar el tiempo que el proceso esperará para hacer la siguiente petición al estado de un informe de cuenta. | PT10M | PT10M |
| datastreams.jwt.issuer | Modificar el issuer de los live events. | https://live-events.canvas.instructure.com | https://live-events.canvas.instructure.com |
| datastreams.jwt.jwks.uri | Modificar la URI del issuer de los live events. | https://8axpcl50e4.execute-api.us-east-1.amazonaws.com/main/jwks | https://8axpcl50e4.execute-api.us-east-1.amazonaws.com/main/jwks |

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
mvn clean install spring-boot:run
```

## Instalación en Canvas

Para instalar la herramienta en Canvas, modifica el fichero `lti.xml` con la información y rutas públicas de la herramienta, usa ese fichero a la hora de [importar una herramienta LTI en Canvas](https://community.canvaslms.com/docs/DOC-10724-67952720325). 

## Configuración de Live Events

Una de las funcionalidades más importantes es la configuración de los data services de Canvas para recibir eventos en tiempo real cuando los profesores modifican una nota.

Para ello en la instancia de Canvas hay que configurar los data services:

https://community.canvaslms.com/t5/Admin-Guide/How-do-I-use-the-Data-Services-Portal/ta-p/258
https://community.canvaslms.com/t5/Admin-Guide/How-do-I-subscribe-to-Live-Events-using-Canvas-Data-Services/ta-p/227
https://community.canvaslms.com/t5/Admin-Guide/How-do-I-configure-and-test-Canvas-Live-Events-using-HTTPS/ta-p/151

En el momento de crear el data stream, se debe seleccionar el método HTTPS, seleccionar la opción de firmar el evento y como endpoint definir la ruta de la aplicación, debe ser algo del estilo:
```
https://lti.application.url/datastream/post
```

`/datastream/post` es la parte importante al ser el endpoint de recepción de eventos.

