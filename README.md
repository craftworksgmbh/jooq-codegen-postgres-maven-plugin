# jOOQ Codegen Postgres Maven Plugin

Maven plugin to generate jOOQ classes using dockerized postgres database during code generation phase

# How to Use
Example setup
```xml
<plugin>
    <groupId>at.craftworks.tools.maven</groupId>
    <artifactId>jooq-codegen-postgres-maven-plugin</artifactId>
    <version>0.0.1</version>
    <executions>
        <execution>
            <id>jooq-codegen</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>jooq-generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <dockerImageName>postgres:11</dockerImageName>
        <flyway>
            <migrationDirectory>filesystem:${basedir}/src/main/resources/db/migration</migrationDirectory>
        </flyway>
        <generator>
            <database>
                <name>org.jooq.meta.postgres.PostgresDatabase</name>
                <includes>.*</includes>
                <excludes>flyway_schema_history.*</excludes>
                <inputSchema>public</inputSchema>
            </database>
            <target>
                <packageName>io.github.myuser.myproject.jooq</packageName>
                <directory>target/generated-sources/jooq</directory>
            </target>
        </generator>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.10.6</version>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>1.10.6</version>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>6.0.3</version>
        </dependency>

        <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq</artifactId>
            <version>3.12.3</version>
        </dependency>
        <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq-meta</artifactId>
            <version>3.12.3</version>
        </dependency>
        <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq-codegen</artifactId>
            <version>3.12.3</version>
        </dependency>
    </dependencies>
</plugin>
```
