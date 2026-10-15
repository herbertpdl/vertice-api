package com.vertice.api.user;

import com.vertice.api.common.exception.ExportTooLargeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedOutputStreamTest {

    @Test
    void writesUpToTheLimit() {
        BoundedOutputStream out = new BoundedOutputStream(10);

        out.write(new byte[8], 0, 8);
        out.write('x');
        out.write('y');

        assertThat(out.size()).isEqualTo(10);
    }

    /** The write that would cross the limit is refused whole: nothing of it is stored. */
    @Test
    void refusesTheWriteThatWouldCrossTheLimit_andStoresNothingOfIt() {
        BoundedOutputStream out = new BoundedOutputStream(10);
        out.write(new byte[8], 0, 8);

        assertThatThrownBy(() -> out.write(new byte[5], 0, 5))
                .isInstanceOf(ExportTooLargeException.class)
                .hasMessage("Data export too large for one response: the document exceeds 10 bytes");
        assertThat(out.size()).isEqualTo(8);

        out.write(new byte[2], 0, 2);
        assertThatThrownBy(() -> out.write('z')).isInstanceOf(ExportTooLargeException.class);
        assertThat(out.size()).isEqualTo(10);
    }
}
