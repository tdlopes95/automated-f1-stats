package com.f1stats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.f1stats.R;

import org.junit.Test;

public class TeamColorsTest {

    @Test
    public void apiColour_withOrWithoutHash() {
        assertEquals(Integer.valueOf(0xFF3671C6), TeamColors.parseApiColour("3671C6"));
        assertEquals(Integer.valueOf(0xFF3671C6), TeamColors.parseApiColour("#3671c6"));
    }

    @Test
    public void apiColour_invalid() {
        assertNull(TeamColors.parseApiColour(null));
        assertNull(TeamColors.parseApiColour(""));
        assertNull(TeamColors.parseApiColour("None"));
        assertNull(TeamColors.parseApiColour("#12345"));
    }

    @Test
    public void constructorId_beatsName() {
        assertEquals(R.color.team_ferrari, TeamColors.resolveRes("ferrari", "McLaren"));
        assertEquals(R.color.team_rb, TeamColors.resolveRes("rb", null));
        assertEquals(R.color.team_bmw_sauber, TeamColors.resolveRes("bmw_sauber", null));
    }

    @Test
    public void unknownConstructorId_fallsBackToName() {
        assertEquals(R.color.team_haas, TeamColors.resolveRes("future_team", "Haas F1 Team"));
    }

    @Test
    public void nameKeywords() {
        assertEquals(R.color.team_rb, TeamColors.resolveRes(null, "Racing Bulls"));
        assertEquals(R.color.team_rb, TeamColors.resolveRes(null, "RB F1 Team"));
        assertEquals(R.color.team_rb, TeamColors.resolveRes(null, "RB"));
        assertEquals(R.color.team_red_bull, TeamColors.resolveRes(null, "Red Bull Racing"));
        assertEquals(R.color.team_sauber, TeamColors.resolveRes(null, "Kick Sauber"));
        assertEquals(R.color.team_bmw_sauber, TeamColors.resolveRes(null, "BMW Sauber"));
        assertEquals(R.color.team_audi, TeamColors.resolveRes(null, "Audi"));
        assertEquals(R.color.team_cadillac, TeamColors.resolveRes(null, "Cadillac F1 Team"));
        assertEquals(R.color.team_haas, TeamColors.resolveRes(null, "Haas F1 Team"));
    }

    @Test
    public void unknown_isNeutralDefault() {
        assertEquals(R.color.team_default, TeamColors.resolveRes(null, null));
        assertEquals(R.color.team_default, TeamColors.resolveRes("", "Minardi"));
    }
}
