package at.craftworks.tools.maven.jooq;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.internal.jdbc.DriverDataSource;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Jdbc;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Based on jooqgen-liquibase-postgres and jOOQ-codegen-maven
 */
@Mojo(
        name = "jooq-generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class Plugin extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    // e.g postgres:11
    @Parameter
    private String dockerImageName;

    /**
     * Locations to scan recursively for migrations. (default: db/migration)
     * Sets the locations to scan recursively for migrations.
     * <p>The location type is determined by its prefix.
     * Unprefixed locations or locations starting with {@code classpath:} point to a package on the classpath and may
     * contain both SQL and Java-based migrations.
     * Locations starting with {@code filesystem:} point to a directory on the filesystem, may only
     * contain SQL migrations and are only scanned recursively down non-hidden directories.</p>
     */
    @Parameter
    private FlywayConfiguration flyway;

    /**
     * The jOOQ generator settings
     */
    @Parameter
    private org.jooq.meta.jaxb.Generator generator;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // [#2887] Patch relative paths to take plugin execution basedir into account
        if (!new File(generator.getTarget().getDirectory()).isAbsolute()) {
            try {
                generator.getTarget().setDirectory(new File(project.getBasedir(), generator.getTarget().getDirectory()).getCanonicalPath());
            } catch (IOException e) {
                throw new MojoExecutionException("Error during resolving target dir", e);
            }
        }

        JdbcDatabaseContainer container = runDatabaseContainer();
        DataSource dataSource = createDataSource(container);
        String[] migrationLocations = {flyway.migrationDirectory};

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            flywayMigrate(dataSource, migrationLocations);

            String schema = this.generator.getDatabase().getInputSchema();
            Jdbc jdbc = getJdbc(container, schema);
            Configuration jooqConfiguration = createJooqConfiguration(jdbc);
            GenerationTool.generate(jooqConfiguration);

        } catch (SQLException e) {
            throw new MojoExecutionException("Error during database connection", e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error during jooq generation", e);
        }

        String compileSourceRoot = generator.getTarget().getDirectory();
        System.out.println("Add compileSourceRoot: " + compileSourceRoot);
        project.addCompileSourceRoot(compileSourceRoot);
    }

    private JdbcDatabaseContainer runDatabaseContainer() {
        JdbcDatabaseContainer databaseContainer = new PostgreSQLContainer(dockerImageName)
                .withDatabaseName("docker_db")
                .withPassword("password")
                .withUsername("username");

        databaseContainer.start();
        return databaseContainer;
    }

    private static DataSource createDataSource(JdbcDatabaseContainer container) {
        return createDataSource(container.getDriverClassName(), container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private Configuration createJooqConfiguration(Jdbc myJdbc) {
        return new Configuration()
                .withJdbc(myJdbc)
                .withGenerator(this.generator);
    }

    private static DataSource createDataSource(String driverClassName, String jdbcUrl, String username, String password) {
        // warning: DriverDataSource is private class from Flyway
        return new DriverDataSource(
                Plugin.class.getClassLoader(),
                driverClassName,
                jdbcUrl,
                username,
                password
        );
    }

    private MigrateResult flywayMigrate(DataSource dataSource, String[] locations) {
        Flyway flyway = Flyway.configure()
                .baselineOnMigrate(false)
                .dataSource(dataSource)
                .locations(locations)
                .load();

        return flyway.migrate();
    }

    private Jdbc getJdbc(JdbcDatabaseContainer databaseContainer, String schema) {
        return new Jdbc()
                .withDriver(databaseContainer.getDriverClassName())
                .withUrl(databaseContainer.getJdbcUrl())
                .withUser(databaseContainer.getUsername())
                .withSchema(schema)
                .withPassword(databaseContainer.getPassword());
    }

    public static class FlywayConfiguration {
        private String migrationDirectory;
    }
}
