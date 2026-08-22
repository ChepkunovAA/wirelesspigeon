package org.corpsman.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void generatesFiveCharAlnumPasswords() {
        for (int i = 0; i < 50; i++) {
            String pwd = PasswordGenerator.generate();
            assertThat(pwd).matches("[A-Za-z0-9]{5}");
        }
    }

    @Test
    void generatesDifferentValues() {
        assertThat(PasswordGenerator.generate()).isNotEqualTo(PasswordGenerator.generate());
    }
}
