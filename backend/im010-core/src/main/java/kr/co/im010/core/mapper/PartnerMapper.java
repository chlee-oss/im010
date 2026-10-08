package kr.co.im010.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import kr.co.im010.core.row.PartnerRow;

@Mapper
public interface PartnerMapper {

    List<PartnerRow> findExposed();
}
