package kr.co.im010.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.InternetForwardTarget;
import kr.co.im010.core.row.InternetProductRow;

@Mapper
public interface InternetProductMapper {

    /** 노출 중인 인터넷 상품. productType 이 null 이면 전체. */
    List<InternetProductRow> findExposed(@Param("productType") String productType);

    InternetForwardTarget findForwardTarget(@Param("id") long id);
}
