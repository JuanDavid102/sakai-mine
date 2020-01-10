package edu.uc.ltigradebook.dao;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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

    @Value("${banner.enabled:false}")
    private boolean bannerEnabled;

    private static HikariDataSource hikariDataSource = null;
    private static JdbcTemplate jdbcTemplate = null;

    @PostConstruct
    public void init() {
        HikariConfig config = new HikariConfig();
        if(!bannerEnabled) {
            log.info("The banner integration is not enabled.");
            return;
        }
        config.setJdbcUrl(bannerDatasourceUrl);
        config.setConnectionInitSql(bannerConnectionInitSql);
        config.setUsername(bannerUsername);
        config.setPassword(bannerPassword);
        config.setDriverClassName(bannerDriverClassName);
        try { 
            if(hikariDataSource == null) {
            	hikariDataSource = new HikariDataSource(config);
            }
            jdbcTemplate = new JdbcTemplate();
            jdbcTemplate.setDataSource(hikariDataSource);
        } catch(Exception e) {
            log.error("Fatal error initializing Banner datasource.", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if(hikariDataSource != null) {
        	hikariDataSource.close();
        }
        hikariDataSource = null;
    }

    private JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    // Checks if the user is the main instructor of the course.
    public boolean isCourseMainInstructor(String ncrCode, String periodId, String teacherId) {
        log.info("isCourseMainInstructor(ncrCode = {}, periodId = {}, teacherId = {})", ncrCode, periodId, teacherId);

        if(!bannerEnabled) {
            return false;
        }

        try {
            //Construct the store procedure call
            String readCourseMainInstructor = String.format("select count(1) from sirasgn, ssbsect where sirasgn_term_code = ssbsect_term_code and sirasgn_crn = ssbsect_crn and "+
                    "sirasgn_primary_ind = 'Y' and "+
                    "SIRASGN_term_code = %s and "+
                    "SIRASGN_PIDM = %s and "+
                    "ssbsect_crn = %s", 
                    periodId, teacherId, ncrCode);
            log.info(readCourseMainInstructor);

            //Call the procedure
            Integer result = getJdbcTemplate().queryForObject(readCourseMainInstructor, Integer.class);

            return result.equals(new Integer(1));
        } catch (Exception e) {
            log.error("Fatal error checking the course main instructor.", e);
        }
        return false;
    }

    // Gets the banner grades from the course, returns a map of RUT - GRADE.
    public Map<String, String> getBannerUserListFromCourse(String ncrCode, String periodId, String teacherId){
        log.info("getBannerUserListFromCourse(ncrCode = {}, periodId = {}, teacherId = {})",ncrCode, periodId, teacherId);
        final String OUT_BANNER_USER_LIST_PARAMETER = "bannerUserList";
        final String IN_NCR_CODE_PARAMETER = "ncrCode";
        final String IN_PERIOD_ID_PARAMETER = "periodId";
        final String IN_TEACHER_ID_PARAMETER = "teacherId";
        Map<String, String> bannerGrades = new HashMap<String, String>();

        if(!bannerEnabled) {
            return bannerGrades;
        }

        try {
            SimpleJdbcCall procedureParametersCall = new SimpleJdbcCall(getJdbcTemplate().getDataSource());
            procedureParametersCall.withFunctionName("pk_adap_15_carga_nota.f_alumnos_curso_web")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlOutParameter(OUT_BANNER_USER_LIST_PARAMETER, OracleTypes.CURSOR, new BannerUserMapper()),
                                   new SqlParameter(IN_NCR_CODE_PARAMETER, Types.VARCHAR),
                                   new SqlParameter(IN_PERIOD_ID_PARAMETER, Types.VARCHAR),
                                   new SqlParameter(IN_TEACHER_ID_PARAMETER, Types.VARCHAR));

            Map<String, Object> result = procedureParametersCall.execute(new MapSqlParameterSource()
                    .addValue(IN_NCR_CODE_PARAMETER, ncrCode)
                    .addValue(IN_PERIOD_ID_PARAMETER, periodId)
                    .addValue(IN_TEACHER_ID_PARAMETER, teacherId));

            if(!result.isEmpty()){
                @SuppressWarnings("unchecked")
				List<BannerUser> bannerUserList = (List<BannerUser>) result.get(OUT_BANNER_USER_LIST_PARAMETER);
                log.info("Found {} users associated to the course in banner.", bannerUserList.size());
                for(BannerUser bannerUser : bannerUserList) {
                    log.info("Adding user {} to the map with grade {}.", bannerUser.getUserRut(), bannerUser.getSFRSTCR_GRDE_CODE());
                    bannerGrades.put(bannerUser.getUserRut(), bannerUser.getSFRSTCR_GRDE_CODE());
                }
            }
        } catch (Exception e) {
           log.error("Fatal error getting users from banner. ", e);
        }
        return bannerGrades;
    }

    //Sends a grade to banner
    public boolean sendGradeToBanner(String cod_ncr, String nota, String rut_alumno, String rut_profesor, String ano_periodo_banner) {
        log.info("sendGradesToBanner(cod_ncr={},nota={},rut_alumno={},rut_profesor={},ano_periodo_banner={})", cod_ncr, nota, rut_alumno, rut_profesor, ano_periodo_banner);

        if(!bannerEnabled) {
            return false;
        }

        try {
            //Construct the store procedure call
            String insertBannerString = String.format("call pk_adap_15_carga_nota.sp_ingresa_calificacion ('%s', '%s', '%s', '%s' , '%s' , '%s')", cod_ncr, nota, rut_alumno, rut_profesor, StringUtils.EMPTY, ano_periodo_banner);
            log.debug(insertBannerString);

            //Call the procedure
            getJdbcTemplate().update(insertBannerString);
            return true;
        } catch (Exception e) {
            log.error("Fatal error sending grade to banner. ", e);
        }
        return false;
    }

}
