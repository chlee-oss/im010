package kr.co.im010.api.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import kr.co.im010.core.mapper.ContentVersionMapper;

@WebMvcTest(controllers = ContentVersionController.class)
class ContentVersionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ContentVersionMapper mapper;

    @Test
    void servesVersionToTheServerItself() throws Exception {
        when(mapper.contentVersion()).thenReturn("abc123");

        mvc.perform(get("/api/internal/content-version").with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("abc123\n"));
    }

    @Test
    void hidesFromOtherAddresses() throws Exception {
        mvc.perform(get("/api/internal/content-version").with(r -> {
                    r.setRemoteAddr("203.0.113.7");
                    return r;
                }))
                .andExpect(status().isNotFound());
    }

    @Test
    void loopbackCheck() {
        assertThat(ContentVersionController.isLoopback("127.0.0.1")).isTrue();
        assertThat(ContentVersionController.isLoopback("::1")).isTrue();
        assertThat(ContentVersionController.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(ContentVersionController.isLoopback("10.0.0.1")).isFalse();
        assertThat(ContentVersionController.isLoopback("")).isFalse();
        assertThat(ContentVersionController.isLoopback(null)).isFalse();
    }
}
