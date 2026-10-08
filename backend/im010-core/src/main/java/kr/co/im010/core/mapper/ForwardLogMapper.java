package kr.co.im010.core.mapper;

import org.apache.ibatis.annotations.Mapper;

import kr.co.im010.core.row.ForwardLog;

@Mapper
public interface ForwardLogMapper {

    void insert(ForwardLog log);
}
