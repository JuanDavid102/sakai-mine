package edu.uc.ltigradebook.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;

import edu.uc.ltigradebook.model.BannerUser;

public class BannerUserMapper implements RowMapper {

    @Override
    public edu.uc.ltigradebook.model.BannerUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        
        BannerUser t = new BannerUser();
        t.setSPRIDEN_LAST_NAME(rs.getString("SPRIDEN_LAST_NAME"));
        t.setSPRIDEN_FIRST_NAME(rs.getString("SPRIDEN_FIRST_NAME"));
        t.setSPRIDEN_ID(rs.getString("SPRIDEN_ID"));
        t.setSFRSTCR_CRN(rs.getString("SFRSTCR_CRN"));
        t.setSFRSTCR_GRDE_CODE(rs.getString("SFRSTCR_GRDE_CODE"));
        t.setSSBSECT_CRSE_NUMB(rs.getString("SSBSECT_CRSE_NUMB"));
        t.setSSBSECT_SUBJ_CODE(rs.getString("SSBSECT_SUBJ_CODE"));
        t.setSSBSECT_SEQ_NUMB(rs.getString("SSBSECT_SEQ_NUMB"));
        t.setUserRut(fixUserRUT(rs.getString("SPRIDEN_ID")));        
        return t;
    }

    //The banner view returns RUTs like 12.345.678-9, we should delete dots and dashes.
    private String fixUserRUT(String input) {
        String output = StringUtils.remove(input, ".");
        output = StringUtils.remove(output, "-");
        return output;
    }

}

