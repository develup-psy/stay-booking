package psy.staybooking.point.application.service;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.point.domain.PointHold;
import psy.staybooking.point.domain.PointHoldStatus;
import psy.staybooking.point.domain.PointWallet;
import psy.staybooking.point.repository.PointWalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointWalletRepository pointWalletRepository;

    @InjectMocks
    private PointService pointService;

    @Test
    void getAvailablePointReturnsWalletAvailableAmount() {
        PointWallet pointWallet = PointWallet.create(1L, 100_000L);
        pointWallet.hold(10L, 20_000L);
        when(pointWalletRepository.findWalletWithHoldsByUserId(1L)).thenReturn(Optional.of(pointWallet));

        long availablePoint = pointService.getAvailablePoint(1L);

        assertThat(availablePoint).isEqualTo(80_000L);
    }

    @Test
    void holdPointCreatesHeldPoint() {
        PointWallet pointWallet = PointWallet.create(1L, 100_000L);
        when(pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(1L)).thenReturn(Optional.of(pointWallet));

        PointHold pointHold = pointService.holdPoint(1L, 10L, 20_000L);

        assertThat(pointHold.getStatus()).isEqualTo(PointHoldStatus.HELD);
        assertThat(pointWallet.getAvailableAmount()).isEqualTo(80_000L);
    }

    @Test
    void commitHoldConsumesWalletAmount() {
        PointWallet pointWallet = PointWallet.create(1L, 100_000L);
        pointWallet.hold(10L, 20_000L);
        when(pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(1L)).thenReturn(Optional.of(pointWallet));

        pointService.commitHold(1L, 10L);

        assertThat(pointWallet.getTotalAmount()).isEqualTo(80_000L);
        assertThat(pointWallet.getHolds().getFirst().getStatus()).isEqualTo(PointHoldStatus.COMMITTED);
    }

    @Test
    void releaseHoldRestoresAvailableAmount() {
        PointWallet pointWallet = PointWallet.create(1L, 100_000L);
        pointWallet.hold(10L, 20_000L);
        when(pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(1L)).thenReturn(Optional.of(pointWallet));

        pointService.releaseHold(1L, 10L);

        assertThat(pointWallet.getAvailableAmount()).isEqualTo(100_000L);
        assertThat(pointWallet.getHolds().getFirst().getStatus()).isEqualTo(PointHoldStatus.RELEASED);
    }

    @Test
    void holdPointThrowsWhenWalletDoesNotExist() {
        when(pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.holdPoint(1L, 10L, 20_000L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void commitHoldUsesLockedWalletLookup() {
        PointWallet pointWallet = PointWallet.create(1L, 100_000L);
        pointWallet.hold(10L, 20_000L);
        when(pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(1L)).thenReturn(Optional.of(pointWallet));

        pointService.commitHold(1L, 10L);

        verify(pointWalletRepository).findWalletWithHoldsByUserIdForUpdate(1L);
    }
}
