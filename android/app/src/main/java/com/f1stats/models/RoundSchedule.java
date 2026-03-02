package com.f1stats.models;

import java.util.List;
import java.util.Map;

public class RoundSchedule {

    private int round;
    private String raceName;
    private String circuit;
    private String country;
    private String raceDate;
    private List<Map<String, Object>> sessions;

    public RoundSchedule(int round, String raceName, String circuit,
                         String country, String raceDate,
                         List<Map<String, Object>> sessions) {
        this.round    = round;
        this.raceName = raceName;
        this.circuit  = circuit;
        this.country  = country;
        this.raceDate = raceDate;
        this.sessions = sessions;
    }

    public int getRound() { return round; }
    public String getRaceName() { return raceName; }
    public String getCircuit() { return circuit; }
    public String getCountry() { return country; }
    public String getRaceDate() { return raceDate; }
    public List<Map<String, Object>> getSessions() { return sessions; }
}