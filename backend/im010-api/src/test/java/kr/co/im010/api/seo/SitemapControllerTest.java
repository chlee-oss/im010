package kr.co.im010.api.seo;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SitemapController.class)
class SitemapControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SitemapService service;

    @Test
    void servesXmlWithUtf8EvenForBrowserAccept() throws Exception {
        when(service.xml()).thenReturn("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset/>\n");

        mvc.perform(get("/sitemap.xml").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "max-age=600, public"))
                .andExpect(content().string("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset/>\n"));
    }
}
