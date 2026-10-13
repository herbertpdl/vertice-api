package com.vertice.api.common.time;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbInstantsTest {

    @Test
    void now_hasNoSubMicrosecondDigits() {
        assertThat(DbInstants.now().getNano() % 1_000).isZero();
    }
}
