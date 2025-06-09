package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.services.GamesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GamesController.class)
class GamesControllerTest {

    @MockitoBean
    private GamesService gamesService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deleteGameSuccessfully() throws Exception {
        final String gameName = "the-last-of-us-3";
        final String studioId = "dev.naughty-dog-11111";
        doNothing().when(gamesService).deleteGame(eq(gameName), eq(gameName));

        mockMvc.perform(delete("/buckets/" + studioId + "/games/" + gameName))
                .andExpect(status().isOk());
    }
}