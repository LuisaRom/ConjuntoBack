package com.example.APP;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AppApplication {

	private static final Logger log = LoggerFactory.getLogger(AppApplication.class);

	public static void main(String[] args) {
		loadEnv();
		// Log seguro
		log.info("Arrancando aplicación (verificando propiedades de BD) ...");
		log.info("spring.datasource.url='{}'", System.getProperty("spring.datasource.url"));
		log.info("spring.datasource.username='{}'", System.getProperty("spring.datasource.username"));
		log.info("spring.datasource.password set? = {}", System.getProperty("spring.datasource.password") != null);

		SpringApplication.run(AppApplication.class, args);
	}

	private static void loadEnv() {
		// Intentamos cargar en varios sitios posibles (working dir, ./APP, ../)
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		if (dotenv.get("DB_URL") == null && dotenv.get("DB_USERNAME") == null) {
			dotenv = Dotenv.configure()
					.directory("./APP")
					.ignoreIfMissing()
					.load();
		}

		if (dotenv.get("DB_URL") == null && dotenv.get("DB_USERNAME") == null) {
			dotenv = Dotenv.configure()
					.directory("..")
					.ignoreIfMissing()
					.load();
		}

		setPropertyFromDotenv(dotenv, "DB_URL", "spring.datasource.url");
		setPropertyFromDotenv(dotenv, "DB_USERNAME", "spring.datasource.username");
		setPropertyFromDotenv(dotenv, "DB_PASSWORD", "spring.datasource.password");
		setPropertyFromDotenv(dotenv, "JWT_SECRET", "app.security.jwt-secret");
		setPropertyFromDotenv(dotenv, "MERCADOPAGO_ACCESS_TOKEN", "mercadopago.access-token");
		setPropertyFromDotenv(dotenv, "MERCADOPAGO_PUBLIC_KEY", "mercadopago.public-key");
		setPropertyFromDotenv(dotenv, "MERCADOPAGO_TEST_PAYER_EMAIL", "mercadopago.test-payer-email");
		setPropertyFromDotenv(dotenv, "FRONTEND_BASE_URL", "app.frontend.base-url");
		setPropertyFromDotenv(dotenv, "BACKEND_BASE_URL", "app.backend.base-url");

		// Fallback para Hibernate (evita error "Unable to determine Dialect" durante debug)
		if (System.getProperty("spring.jpa.database-platform") == null) {
			System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
		}
	}

	private static void setPropertyFromDotenv(Dotenv dotenv, String envKey, String springKey) {
		String value = dotenv.get(envKey);
		if (value != null && !value.isEmpty()) {
			System.setProperty(envKey, value);
			System.setProperty(springKey, value);
		}
	}
}
