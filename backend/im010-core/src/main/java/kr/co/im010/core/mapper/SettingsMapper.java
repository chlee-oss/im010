package kr.co.im010.core.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.AdminListRow;
import kr.co.im010.core.row.AuditListRow;
import kr.co.im010.core.row.GroupRow;

/** 환경설정: ST-01 프로그램 · ST-02 관리자 · ST-03 권한 그룹 · ST-05 접속이력 · 백오피스 설정(IP 제한). */
@Mapper
public interface SettingsMapper {

    // ---------- ST-01 ----------

    int updateProgram(@Param("id") String id, @Param("name") String name, @Param("sortOrder") int sortOrder,
                      @Param("enabled") boolean enabled);

    // ---------- ST-02 ----------

    List<AdminListRow> findAdmins();

    AdminListRow findAdmin(@Param("id") long id);

    boolean existsLoginId(@Param("loginId") String loginId);

    long insertAdmin(@Param("loginId") String loginId, @Param("name") String name, @Param("dept") String dept,
                     @Param("phone") String phone, @Param("groupId") long groupId, @Param("passwordHash") String passwordHash);

    void updateAdmin(@Param("id") long id, @Param("name") String name, @Param("dept") String dept,
                     @Param("phone") String phone, @Param("groupId") long groupId, @Param("status") String status);

    void unlock(@Param("id") long id);

    /** 비밀번호 초기화: 임시 비밀번호 + 다음 로그인 때 변경 강제, 잠금 해제 */
    void resetPassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    /** OTP 초기화: 다음 로그인 때 OTP 앱을 다시 등록 */
    void resetOtp(@Param("id") long id);

    /** 사용 중인 최고관리자 수 (마지막 최고관리자는 그룹 변경 · 퇴사 처리 불가) */
    int countActiveSuperAdmins();

    String findSetting(@Param("key") String key);

    void updateSetting(@Param("key") String key, @Param("value") String value, @Param("by") String by);

    // ---------- ST-03 ----------

    List<GroupRow> findGroups();

    GroupRow findGroup(@Param("id") long id);

    long insertGroup(@Param("code") String code, @Param("name") String name);

    void updateGroupName(@Param("id") long id, @Param("name") String name);

    void deleteGroup(@Param("id") long id);

    void deletePermissions(@Param("groupId") long groupId);

    void insertPermission(@Param("groupId") long groupId, @Param("programId") String programId,
                          @Param("actions") String[] actions);

    // ---------- ST-05 ----------

    List<AuditListRow> findAudits(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                  @Param("kind") String kind, @Param("loginId") String loginId,
                                  @Param("action") String action, @Param("limit") int limit, @Param("offset") int offset);

    int countAudits(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("kind") String kind,
                    @Param("loginId") String loginId, @Param("action") String action);
}
