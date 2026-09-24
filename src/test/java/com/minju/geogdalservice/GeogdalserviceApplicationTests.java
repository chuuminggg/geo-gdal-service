package com.minju.geogdalservice;

import com.minju.geogdalservice.support.PostgisTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PostgisTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class GeogdalserviceApplicationTests {

	@Test
	void contextLoads() {
	}

}
