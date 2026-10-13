package com.vertice.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Non-local profile: JwtSecretGuard refuses the dev default secret, so supply a valid one.
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=3",
		"vertice.jwt.secret=non-local-test-secret-of-32-bytes!"})
class VerticeApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
