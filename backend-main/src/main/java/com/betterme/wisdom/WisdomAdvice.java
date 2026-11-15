package com.betterme.wisdom;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WisdomAdvice {

    private final DailyQuoteService service;

    public WisdomAdvice(DailyQuoteService service) {
        this.service = service;
    }

    @ModelAttribute("quote")
    public DailyQuoteService.Quote quote() {
        return service.random();
    }
}