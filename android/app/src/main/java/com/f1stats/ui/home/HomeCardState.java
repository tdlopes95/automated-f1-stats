package com.f1stats.ui.home;

import com.f1stats.home.HomeCardType;
import com.f1stats.models.NewsResponse;
import com.f1stats.models.OnThisDayResponse;
import com.f1stats.models.WeatherForecast;
import com.f1stats.util.HeadToHead;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What a Home card shows. Survives view recycling; the holders only render it. */
public class HomeCardState {

    public enum Status { IDLE, LOADING, LOADED, ERROR }

    public Status status = Status.IDLE;
    /** True once the card has something to show (from the cache or the network). */
    public boolean hasData;
    public String error;

    /**
     * Shown instead of the content when set: a card that needs a choice, or whose choice
     * isn't in this season. The action opens the options for {@link #promptTarget}.
     */
    public String promptMessage;
    public String promptAction;
    public HomeCardType promptTarget;

    public void clearPrompt() {
        promptMessage = null;
        promptAction = null;
        promptTarget = null;
    }

    public void setPrompt(String message, String action, HomeCardType target) {
        promptMessage = message;
        promptAction = action;
        promptTarget = target;
    }

    /** One driver row (championship P1/P2, last winner). */
    public static class DriverRow {
        public String driverId;
        public String name;
        public String team;
        public String points;
        public String constructorId;
        public String code;
    }

    public static class NextRace extends HomeCardState {
        /** Null with {@link #offseason} set when there is no upcoming race. */
        public Map<String, Object> race;
        public boolean offseason;
        public String circuitImageUrl;
    }

    public static class Championship extends HomeCardState {
        public boolean seasonStarted = true;
        public DriverRow leader;
        public DriverRow p2;
        public double gap;
        public String insight;
    }

    public static class LastWinner extends HomeCardState {
        public DriverRow winner;
        public String raceName;
    }

    public static class FavouriteDriver extends HomeCardState {
        public int year;
        public String driverId, code, name, team, constructorId, nationality, number;
        public int position;
        public double points;
        /** Points behind the driver one place ahead; 0 for the leader. */
        public double gapToAhead;
        public int wins, podiums;
        public final List<HeadToHead.FormEntry> form = new ArrayList<>();
    }

    public static class FavouriteTeam extends HomeCardState {
        public int year;
        public String constructorId, name;
        public int position;
        public double points;
        /** Points behind the team one place ahead; 0 for the leader. */
        public double gapToAhead;
        /** The team's two highest-placed drivers; driverB null if only one raced. */
        public DriverRow driverA, driverB;
        public int h2hA, h2hB;
    }

    public static class PinnedH2h extends HomeCardState {
        public int year;
        public Side driver1, driver2;

        public static class Side {
            public String driverId, name, team, constructorId;
            public HeadToHead.DriverStats stats;
        }
    }

    public static class WeekendWeather extends HomeCardState {
        /** The next race's forecast; null with {@link #noRace} set in the offseason. */
        public WeatherForecast forecast;
        public boolean noRace;
    }

    public static class News extends HomeCardState {
        /** The newest headlines from the chosen sources, at most {@link #MAX_ITEMS}. */
        public static final int MAX_ITEMS = 5;
        public final List<NewsResponse.Item> items = new ArrayList<>();
        /** The response had headlines, just none from the chosen sources. */
        public boolean noneFromSources;
    }

    public static class OnThisDay extends HomeCardState {
        public static final int MAX_ITEMS = 4;
        public final List<OnThisDayResponse.Item> items = new ArrayList<>();
    }

    public static class Snapshot extends HomeCardState {
        public int year;
        public boolean constructors;
        public final List<SnapshotRow> rows = new ArrayList<>();
        /** The favourite, when it's outside the rows shown. */
        public SnapshotRow favourite;

        public static class SnapshotRow {
            public int position;
            public String name, team, constructorId;
            public double points;
            public double gapToLeader;
            public boolean highlighted;
        }
    }
}
