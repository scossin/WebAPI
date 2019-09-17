/*
 *
 * Copyright 2017 Observational Health Data Sciences and Informatics
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Maria Pozhidaeva
 *
 */
package org.ohdsi.webapi.service;

import static org.ohdsi.webapi.service.SqlRenderService.translateSQL;

import java.util.*;
import java.util.stream.Collectors;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.odysseusinc.arachne.commons.types.DBMSType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.ohdsi.circe.helper.ResourceHelper;
import org.ohdsi.webapi.sqlrender.SourceStatement;
import org.ohdsi.webapi.sqlrender.TranslatedStatement;
import org.springframework.stereotype.Component;

@Path("/ddl/")
@Component
public class DDLService {

	public static final String VOCAB_SCHEMA = "vocab_schema";
	public static final String RESULTS_SCHEMA = "results_schema";
	public static final String CEM_SCHEMA = "cem_results_schema";
	public static final String TEMP_SCHEMA = "oracle_temp_schema";


	private static final String RESULT_DDL_ROOT = "/ddl/results";
	private static final Collection<String> RESULT_DDL_FILE_NAMES = Arrays.asList(
		"cohort.sql",
		"cohort_features.sql",
		"cohort_features_analysis_ref.sql",
		"cohort_features_dist.sql",
		"cohort_features_ref.sql",
		"cohort_inclusion.sql",
		"cohort_inclusion_result.sql",
		"cohort_inclusion_stats.sql",
		"cohort_summary_stats.sql",
		"cohort_censor_stats.sql",
		"feas_study_inclusion_stats.sql",
		"feas_study_index_stats.sql",
		"feas_study_result.sql",
		"heracles_analysis.sql",
		"heracles_heel_results.sql",
		"heracles_results.sql",
		"heracles_results_dist.sql",
		"ir_analysis_dist.sql",
		"ir_analysis_result.sql",
		"ir_analysis_strata_stats.sql",
		"ir_strata.sql",
		"heracles_periods.sql",
		"cohort_characterizations.sql",
		"pathway_analysis_codes.sql",
		"pathway_analysis_events.sql",
		"pathway_analysis_paths.sql",
		"pathway_analysis_stats.sql"
	);

	private static final String INIT_HERACLES_PERIODS = "/ddl/results/init_heracles_periods.sql";

	public static final Collection<String> RESULT_INIT_FILE_PATHS = Arrays.asList(
		"/ddl/results/init_heracles_analysis.sql", INIT_HERACLES_PERIODS
	);

	public static final Collection<String> HIVE_RESULT_INIT_FILE_PATHS = Arrays.asList(
		"/ddl/results/init_hive_heracles_analysis.sql", INIT_HERACLES_PERIODS
	);

	public static final Collection<String> INIT_CONCEPT_HIERARCHY_FILE_PATHS = Arrays.asList(
			"/ddl/results/concept_hierarchy.sql",
			"/ddl/results/init_concept_hierarchy.sql"
	);

	private static final Collection<String> RESULT_INDEX_FILE_PATHS = Arrays.asList(
		"/ddl/results/create_index.sql",
		"/ddl/results/pathway_analysis_events_indexes.sql"
	);

	private static final Collection<String> CEMRESULT_DDL_FILE_PATHS = Arrays.asList(
		"/ddl/cemresults/nc_results.sql"
	);

	public static final Collection<String> CEMRESULT_INIT_FILE_PATHS = Arrays.asList();

	private static final Collection<String> CEMRESULT_INDEX_FILE_PATHS = Arrays.asList();

	private static final Collection<String> DBMS_NO_INDEXES = Arrays.asList("redshift", "impala", "netezza");

	@GET
	@Path("results")
	@Produces("text/plain")
	public String generateResultSQL(
			@QueryParam("dialect") String dialect,
			@DefaultValue("vocab") @QueryParam("vocabSchema") String vocabSchema,
			@DefaultValue("results") @QueryParam("schema") String resultSchema,
			@DefaultValue("true") @QueryParam("initConceptHierarchy") Boolean initConceptHierarchy,
			@QueryParam("tempSchema") String tempSchema) {

		Collection<String> resultDDLFilePaths = getResultDDLFilePaths(dialect);

		if (initConceptHierarchy) {
			resultDDLFilePaths.addAll(INIT_CONCEPT_HIERARCHY_FILE_PATHS);
		}
		String oracleTempSchema = ObjectUtils.firstNonNull(tempSchema, resultSchema);
		Map<String, String> params = new HashMap<String, String>() {{
			put(VOCAB_SCHEMA, vocabSchema);
			put(RESULTS_SCHEMA, resultSchema);
			put(TEMP_SCHEMA, oracleTempSchema);
		}};

		return generateSQL(dialect, params, resultDDLFilePaths, getResultInitFilePaths(dialect), RESULT_INDEX_FILE_PATHS);
	}

	private Collection<String> getResultInitFilePaths(String dialect) {
		if (Objects.equals(DBMSType.HIVE.getOhdsiDB(), dialect)) {
			return HIVE_RESULT_INIT_FILE_PATHS;
		} else {
			return RESULT_INIT_FILE_PATHS;
		}
	}

	@GET
	@Path("cemresults")
	@Produces("text/plain")
	public String generateCemResultSQL(@QueryParam("dialect") String dialect, @DefaultValue("cemresults") @QueryParam("schema") String schema) {

		Map<String, String> params = new HashMap<String, String>() {{
			put(CEM_SCHEMA, schema);
		}};

		return generateSQL(dialect, params, CEMRESULT_DDL_FILE_PATHS, CEMRESULT_INIT_FILE_PATHS, CEMRESULT_INDEX_FILE_PATHS);
	}

	private String generateSQL(String dialect, Map<String, String> params, Collection<String> filePaths, Collection<String> initFilePaths, Collection<String> indexFilePaths) {
		StringBuilder sqlBuilder = new StringBuilder();
		for (String fileName : filePaths) {
			sqlBuilder.append("\n").append(ResourceHelper.GetResourceAsString(fileName));
		}

		for (String fileName : initFilePaths) {
			sqlBuilder.append("\n").append(ResourceHelper.GetResourceAsString(fileName));
		}

		if (dialect == null || DBMS_NO_INDEXES.stream().noneMatch(dbms -> dbms.equals(dialect.toLowerCase()))) {
			for (String fileName : indexFilePaths) {
				sqlBuilder.append("\n").append(ResourceHelper.GetResourceAsString(fileName));
			}
		}
		String result = sqlBuilder.toString();
		if (dialect != null) {
			result = translateSqlFile(result, dialect, params);
		}
		return result.replaceAll(";", ";\n");
	}

	private List<String> getResultDDLFilePaths(String dialect) {
		return new ArrayList<>(RESULT_DDL_FILE_NAMES).stream()
				.map(fileName -> {
					String filePath = String.format("%s/%s", RESULT_DDL_ROOT, fileName);
					if (StringUtils.isEmpty(dialect)) {
						return filePath;
					}

					String dialectSpecificFilePath = String.format("%s/%s/%s", RESULT_DDL_ROOT, StringUtils.lowerCase(dialect), fileName);
					if (isResourceFileExists(dialectSpecificFilePath)) {
						return dialectSpecificFilePath;
					}

					return filePath;
				})
				.collect(Collectors.toList());
	}

	private boolean isResourceFileExists(String fileName) {
		return ResourceHelper.class.getResource(fileName) != null;
	}
	private String translateSqlFile(String sql, String dialect, Map<String, String> params) {

		SourceStatement statement = new SourceStatement();
		statement.targetDialect = dialect.toLowerCase();
		statement.oracleTempSchema = params.get(TEMP_SCHEMA);
		statement.sql = sql;
		statement.parameters = new HashMap<>(params);
		TranslatedStatement translatedStatement = translateSQL(statement);
		return translatedStatement.targetSQL;
	}

}
