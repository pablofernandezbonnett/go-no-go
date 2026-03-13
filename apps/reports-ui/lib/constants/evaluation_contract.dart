const evaluatePageRoute = '/evaluate';

const healthApiPath = '/api/health';
const reportsIndexApiPath = '/api/reports/index';
const evaluateOptionsApiPath = '/api/evaluate/options';
const evaluateUrlHistoryApiPath = '/api/evaluate/url-history';
const evaluateApiPath = '/api/evaluate';

const jsonContentType = 'application/json';

const evaluationErrorOptionsLoadFailed = 'evaluate_options_load_failed';
const evaluationErrorHistoryLoadFailed = 'evaluate_history_load_failed';
const evaluationErrorInvalidPayload = 'invalid_payload';
const evaluationErrorInvalidRequest = 'invalid_request';
const evaluationErrorEvaluationFailed = 'evaluation_failed';

const inputModeUrl = 'url';
const inputModeRawText = 'raw_text';
const historySourceKindAdHoc = 'ad_hoc';
const historySourceKindPipelineJob = 'pipeline_job';
const allPersonasOptionId = 'all_personas';

const candidateProfileAuto = 'auto';
const candidateProfileNone = 'none';

const adHocEvaluationsDirectory = 'ad-hoc-evaluations';

const engineRootEnvVar = 'ENGINE_ROOT';
const engineGradlewEnvVar = 'ENGINE_GRADLEW';
const reportsUiPortEnvVar = 'REPORTS_UI_PORT';
const fallbackPortEnvVar = 'PORT';
