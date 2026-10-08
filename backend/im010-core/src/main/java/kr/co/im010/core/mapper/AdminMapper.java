package kr.co.im010.core.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.AdminUserRow;
import kr.co.im010.core.row.AuditRow;
import kr.co.im010.core.row.PermissionRow;
import kr.co.im010.core.row.ProgramRow;

/** 관리자 계정 · 권한 · 접속 이력 (CM-01 · ST-01 · ST-02 · ST-03 · ST-05). */
@Mapper
public interface AdminMapper {

    AdminUserRow findUserByLoginId(@Param("loginId") String loginId);

    AdminUserRow findUserById(@Param("id") long id);

    int countUsers();

    void insertUser(@Param("loginId") String loginId, @Param("name") String name,
                    @Param("groupCode") String groupCode, @Param("passwordHash") String passwordHash);

    void updateLoginFailure(@Param("id") long id, @Param("failedCount") int failedCount,
                            @Param("lockedUntil") OffsetDateTime lockedUntil);

    void recordLoginSuccess(@Param("id") long id);

    void enableOtp(@Param("id") long id, @Param("secret") String secret, @Param("step") long step);

    void updateOtpStep(@Param("id") long id, @Param("step") long step);

    void updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    List<PermissionRow> findPermissions(@Param("groupId") long groupId);

    List<ProgramRow> findPrograms();

    void insertAudit(AuditRow audit);
}
