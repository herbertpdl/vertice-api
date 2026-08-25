package com.vertice.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
class VerticeApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
