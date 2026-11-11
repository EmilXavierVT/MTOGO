
package app.entities.orderFlow;

import app.entities.enums.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void getStatus() {
        Order order = new Order();
        order.setStatus(Status.PENDING);

        assertEquals(Status.PENDING, order.getStatus());
    }
}
