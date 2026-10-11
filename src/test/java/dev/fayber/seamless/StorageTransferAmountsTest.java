package dev.fayber.seamless;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageTransferAmountsTest {
    @Test void leftClickTakesEntireStack() {
        assertEquals(46, StorageTransferAmounts.withdrawal(46, 0));
        assertEquals(64, StorageTransferAmounts.withdrawal(64, 0));
        assertEquals(1, StorageTransferAmounts.withdrawal(1, 0));
    }
    @Test void rightClickTakesHalfRoundedUp() {
        assertEquals(23, StorageTransferAmounts.withdrawal(46, 1));
        assertEquals(33, StorageTransferAmounts.withdrawal(65, 1));
        assertEquals(1, StorageTransferAmounts.withdrawal(1, 1));
    }
    @Test void leftClickDepositsFullStackUpToSpaceAvailable() {
        assertEquals(12, StorageTransferAmounts.deposit(12, 17, 64, 0));
        assertEquals(1, StorageTransferAmounts.deposit(12, 63, 64, 0));
        assertEquals(0, StorageTransferAmounts.deposit(12, 64, 64, 0));
    }
    @Test void rightClickDepositsOneLikeVanilla() {
        assertEquals(1, StorageTransferAmounts.deposit(12, 17, 64, 1));
        assertEquals(0, StorageTransferAmounts.deposit(12, 64, 64, 1));
    }
    @Test void unknownMouseButtonsCannotTakeItems() {
        assertEquals(0, StorageTransferAmounts.withdrawal(46, 2));
        assertEquals(0, StorageTransferAmounts.withdrawal(0, 0));
    }
}
