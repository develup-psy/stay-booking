package psy.staybooking.point.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.point.domain.PointHold;
import psy.staybooking.point.domain.PointWallet;
import psy.staybooking.point.repository.PointWalletRepository;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointWalletRepository pointWalletRepository;

    @Transactional(readOnly = true)
    public long getAvailablePoint(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        PointWallet pointWallet = pointWalletRepository.findWalletWithHoldsByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "포인트 지갑 정보를 찾을 수 없습니다."));

        return pointWallet.getAvailableAmount();
    }

    @Transactional
    public PointHold holdPoint(Long userId, Long bookingId, long amount) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        PointWallet pointWallet = pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "포인트 지갑 정보를 찾을 수 없습니다."));

        return pointWallet.hold(bookingId, amount);
    }

    @Transactional
    public void commitHold(Long userId, Long bookingId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        PointWallet pointWallet = pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "포인트 지갑 정보를 찾을 수 없습니다."));

        pointWallet.commitHold(bookingId);
    }

    @Transactional
    public void releaseHold(Long userId, Long bookingId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        PointWallet pointWallet = pointWalletRepository.findWalletWithHoldsByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "포인트 지갑 정보를 찾을 수 없습니다."));

        pointWallet.releaseHold(bookingId);
    }
}
