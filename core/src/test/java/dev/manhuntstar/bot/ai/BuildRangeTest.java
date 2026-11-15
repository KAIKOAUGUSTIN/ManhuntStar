package dev.manhuntstar.bot.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildRangeTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void aBuildStartsOnlyWithinThreeBlocks() {
        BuildRange range = new BuildRange();
        assertTrue(range.allows(PLAYER, 0.0, 0), "right next to the target");
        assertTrue(range.allows(PLAYER, BuildRange.START, 0), "at exactly 3 blocks");
        assertFalse(range.allows(PLAYER, BuildRange.START + 0.01, 0), "past 3 blocks with nothing built yet");
        assertFalse(range.keeps(PLAYER, 0), "nothing built: no build under way");
    }

    @Test
    void aBuildUnderWayKeepsGoingHoweverFarTheTargetGoes() {
        BuildRange range = new BuildRange();
        range.placed(PLAYER, 1000);
        for (double horizontal : new double[] {4, 12, 30, 100}) {
            assertTrue(range.allows(PLAYER, horizontal, 1001), "the target bridged " + horizontal + " blocks away on top of its tower");
        }
        assertTrue(range.allows(PLAYER, 50, 1000 + BuildRange.KEEP_TICKS), "at the time limit");
    }

    @Test
    void aBuildEndsAfterAWhileWithoutABlock() {
        BuildRange range = new BuildRange();
        range.placed(PLAYER, 1000);
        assertFalse(range.allows(PLAYER, 6.0, 1001 + BuildRange.KEEP_TICKS), "no block for too long");
        assertFalse(range.keeps(PLAYER, 1001 + BuildRange.KEEP_TICKS));
        assertTrue(range.allows(PLAYER, 2.0, 1001 + BuildRange.KEEP_TICKS), "close again: a new build may start");
    }

    @Test
    void aTallTowerAtThePaceOfTheBlockLimitNeverGivesUp() {
        BuildRange range = new BuildRange();
        long now = 0;
        range.placed(PLAYER, now);
        // 60 blocks at the speed Bot.placeBlock allows (10 blocks, then a wait of up to 10 s), the player moving away
        for (int block = 0; block < 60; block++) {
            now += block % 10 == 9 ? 200 : 5;
            assertTrue(range.allows(PLAYER, 3.0 + block, now), "gave up the tower at block " + block);
            range.placed(PLAYER, now);
        }
    }

    @Test
    void aBuildEndsWhenTheBotIsTeleportedAway() {
        BuildRange range = new BuildRange();
        range.placed(PLAYER, 1000);
        range.clear();
        assertFalse(range.keeps(PLAYER, 1001), "the build stayed where the bot was");
        assertFalse(range.allows(PLAYER, 60.0, 1001), "landed far from the target: doesn't build from there");
        assertTrue(range.allows(PLAYER, 2.0, 1001), "close to the target: a new build may start");
    }

    @Test
    void aBuildIsForOneTarget() {
        BuildRange range = new BuildRange();
        range.placed(PLAYER, 0);
        assertFalse(range.keeps(OTHER, 1), "another player");
        assertFalse(range.allows(OTHER, 5.0, 1), "another player, past 3 blocks");
        assertFalse(range.keeps(null, 1), "no target");
        range.placed(OTHER, 2);
        assertFalse(range.keeps(PLAYER, 3), "the build moved to another target");
    }
}
