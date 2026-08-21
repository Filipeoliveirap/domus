package com.domus.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
class ApiApplicationTests implements PostgresTestContainerSupport {

	@Test
	void contextLoads() {
	}

}
