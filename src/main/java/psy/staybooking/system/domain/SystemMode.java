package psy.staybooking.system.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;

@Getter
@Entity
@Table(name = "system_modes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemMode {

    private static final byte SINGLE_ROW_ID = 1;

    @Id
    private Byte systemModeId;

    @Enumerated(EnumType.STRING)
    private SystemModeType mode;

    private String reason;

    private String updatedBy;

    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PACKAGE)
    SystemMode(Byte systemModeId, SystemModeType mode, String reason, String updatedBy, LocalDateTime updatedAt) {
        this.systemModeId = systemModeId;
        this.mode = mode;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public static SystemMode initialize(LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "운영 모드 초기화 시각은 필수입니다.");
        }

        return SystemMode.builder()
            .systemModeId(SINGLE_ROW_ID)
            .mode(SystemModeType.REDIS_NORMAL)
            .reason("initialization")
            .updatedBy("system")
            .updatedAt(now)
            .build();
    }

    public void switchMode(SystemModeType nextMode, String reason, String updatedBy, LocalDateTime now) {
        if (nextMode == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "다음 운영 모드는 필수입니다.");
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "운영 모드 변경 주체는 필수입니다.");
        }
        if (now == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "운영 모드 변경 시각은 필수입니다.");
        }

        this.mode = nextMode;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }
}
