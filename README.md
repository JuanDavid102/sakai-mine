# LTI Gradebook

La presente herramienta es un libro de calificaciones que se integra en Canvas via LTI desarrollado para la Pontificia Universidad Católica de Chile por parte de [Entornos de Formación](https://www.edf.global).

La herramienta se apoya en la librería `lti-launch` desarrollada por Kansas State, en su variante utilizada por Oxford University. También se apoya en la librería `canvas-api` desarrollada por Kansas State con algunos añadidos por parte de [Entornos de Formación](https://www.edf.global).

## Requisitos
- Maven 3.6.2 o superior
- Java 1.8.0_231 o superior
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
La aplicación debe configurase utilizando el fichero de ejemplo "application.properties", situado en la carpeta src\main\resources. Recomendamos alojar dicho fichero fuera de la aplicación por cuestiones de seguridad.

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
| server.port | Puerto donde se ejecutará la aplicación | 8443 | 8443 |
| security.require-ssl| Habilitar modo seguro de la aplicación (HTTPS) | true | true |
| server.ssl.key-store-type | Tipo de certificado a utilizar en modo seguro | - | PKCS12  |
| server.ssl.key-store | Ruta del fichero de almacén de claves para ficheros autogenerados. | - | /home/user/keystore.p12 |
| server.ssl.key-store-password | Password del almacén de claves | - | changeit |
| server.ssl.key-alias | Alias del almacén de claves | - | keystore_alias |
| lti-gradebook.canvas_api_token | Token de API compartido que utilizará la aplicación para realizar llamadas | - | - |
| lti-gradebook.admins | Listado de usuarios de Canvas que son administradores de la aplicación, separados por comas. | - | mpellicer,jdoe,hsolo |

## Ejecución
Para ejecutar el código necesitamos configurar correctamente la aplicación y lanzar el siguiente comando:

```bash
java -jar target/lti-gradebook.war -Dspring.config.location=/path/to/configuration/application.properties
```
Donde `/path/to/configuration/application.properties` hace referencia a la ruta donde está alojado el fichero application.properties

## Instalación en Canvas
----------------------

Para instalar la herramienta en Canvas, modifica el fichero `lti.xml` con la información y rutas públicas de la herramienta, usa ese fichero a la hora de [importar una herramienta LTI en Canvas](https://community.canvaslms.com/docs/DOC-10724-67952720325). 

