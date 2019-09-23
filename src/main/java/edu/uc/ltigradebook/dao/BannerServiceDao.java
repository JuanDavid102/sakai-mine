package edu.uc.ltigradebook.dao;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import edu.uc.ltigradebook.model.BannerUser;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;

@Slf4j
@Service
public class BannerServiceDao {

    @Value("${banner.datasource.username:banner}")
    private String bannerUsername;

    @Value("${banner.datasource.password:banner}")
    private String bannerPassword;

    @Value("${banner.datasource.driver-class-name:oracle.jdbc.driver.OracleDriver}")
    private String bannerDriverClassName;

    @Value("${banner.datasource.connectionInitSql:banner}")
    private String bannerConnectionInitSql;

    @Value("${banner.datasource.url:banner}")
    private String bannerDatasourceUrl;
    
    private static HikariDataSource ds;

    private JdbcTemplate getJdbcTemplate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(bannerDatasourceUrl);
        config.setConnectionInitSql(bannerConnectionInitSql);
        config.setUsername(bannerUsername);
        config.setPassword(bannerPassword);
        config.setDriverClassName(bannerDriverClassName);
        if(ds == null) {
        	ds = new HikariDataSource(config);
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return jdbcTemplate;
    }

    // Gets the banner grades from the course, returns a map of RUT - GRADE.
    public Map<String, String> getBannerUserListFromCourse(String ncrCode,String periodId, String teacherId){
        log.info("getBannerUserListFromCourse(ncrCode = {}, periodId = {}, teacherId = {})",ncrCode, periodId, teacherId);
        Map<String, String> bannerGrades = new HashMap<String, String>();
        try {
            SimpleJdbcCall procedureParametersCall = new SimpleJdbcCall(getJdbcTemplate().getDataSource());
            procedureParametersCall.withFunctionName("pk_adap_15_carga_nota.f_alumnos_curso_web")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlOutParameter("bannerUserList", OracleTypes.CURSOR, new BannerUserMapper()),
                                   new SqlParameter("ncrCode", Types.VARCHAR),
                                   new SqlParameter("periodId", Types.VARCHAR),
                                   new SqlParameter("teacherId", Types.VARCHAR));

            Map<String, Object> result = procedureParametersCall.execute(new MapSqlParameterSource()
                    .addValue("ncrCode", ncrCode)
                    .addValue("periodId", periodId)
                    .addValue("teacherId", teacherId));

            if(!result.isEmpty()){
                List<BannerUser> bannerUserList = (ArrayList<BannerUser>) result.get("bannerUserList");
            	log.info("Found {} users associated to the course in banner.", bannerUserList.size());
                for(BannerUser bannerUser : bannerUserList) {
                	log.info("Adding user {} to the map with grade {}.", bannerUser.getUserRut(), bannerUser.getSFRSTCR_GRDE_CODE());
                    bannerGrades.put(bannerUser.getUserRut(), bannerUser.getSFRSTCR_GRDE_CODE());
                }
            }
        } catch (Exception e) {
           log.error("Fatal error getting users from banner. ", e);
        } finally {
        	ds.close();
        	ds = null;
        }
        return bannerGrades;
    }

    //Sends a grade to banner
    public boolean sendGradeToBanner(String cod_ncr, String nota, String rut_alumno, String rut_profesor, String ano_periodo_banner) {
        log.info("sendGradesToBanner(cod_ncr={},nota={},rut_alumno={},rut_profesor={},ano_periodo_banner={})", cod_ncr, nota, rut_alumno, rut_profesor, ano_periodo_banner);
        
        try {
            //Construct the store procedure call
            String insertBannerString = String.format("call pk_adap_15_carga_nota.sp_ingresa_calificacion ('%s', '%s', '%s', '%s' , '%s' , '%s')", cod_ncr, nota, rut_alumno, rut_profesor, StringUtils.EMPTY, ano_periodo_banner);
            log.debug(insertBannerString);
            
            //Call the procedure
            getJdbcTemplate().update(insertBannerString);
            return true;
        } catch (Exception e) {
            log.error("Fatal error sending grade to banner. ", e);
        } finally {
        	ds.close();
        	ds = null;
        }
        return false;
    }

}
