package com.mvelelo.licensing.admin.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** View helper — call from Thymeleaf as ${@fmt.date(...)} / ${@fmt.datetime(...)}. */
@Component("fmt")
public class Fmt {

    private final ZoneId zone = ZoneId.systemDefault();
    private final DateTimeFormatter d  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String date(Instant i)     { return i == null ? "—" : d.format(i.atZone(zone)); }
    public String datetime(Instant i) { return i == null ? "—" : dt.format(i.atZone(zone)); }
}
