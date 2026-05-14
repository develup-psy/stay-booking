package psy.staybooking.point.domain;

import org.junit.jupiter.api.Test;
import psy.staybooking.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointWalletTest {

    @Test
    void addHoldConnectsHoldToWallet() {
        PointWallet wallet = PointWallet.create(1L, 100_000L);
        PointHold hold = wallet.hold(10L, 20_000L);

        assertThat(wallet.getHolds()).hasSize(1);
        assertThat(hold.getWallet()).isEqualTo(wallet);
        assertThat(hold.getStatus()).isEqualTo(PointHoldStatus.HELD);
    }

    @Test
    void decreaseReducesWalletAmount() {
        PointWallet wallet = PointWallet.create(1L, 100_000L);

        wallet.decrease(30_000L);

        assertThat(wallet.getTotalAmount()).isEqualTo(70_000L);
    }

    @Test
    void decreaseThrowsWhenAmountIsInsufficient() {
        PointWallet wallet = PointWallet.create(1L, 10_000L);

        assertThatThrownBy(() -> wallet.decrease(20_000L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void holdCanBeCommittedOnlyOnce() {
        PointWallet wallet = PointWallet.create(1L, 100_000L);
        wallet.hold(10L, 20_000L);
        wallet.commitHold(10L);

        assertThat(wallet.getHolds().getFirst().getStatus()).isEqualTo(PointHoldStatus.COMMITTED);
        assertThat(wallet.getTotalAmount()).isEqualTo(80_000L);
        assertThatThrownBy(() -> wallet.releaseHold(10L)).isInstanceOf(BusinessException.class);
    }
}
