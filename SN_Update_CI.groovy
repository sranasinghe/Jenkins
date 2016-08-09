def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def SNGET_CI(def sys_id,def sn_endpoint ){
	
    withCredentials([[$class: 'UsernamePasswordBinding', credentialsId:SN_CRED_ID, variable: 'SN_CREDS']]){
	  command='curl -f -X GET -H  "Content-Type:application/json" --user ${SN_CREDS} "'+SN_API_URL+sn_endpoint+'/'+sys_id+'" > servicenow_result.json 2>/dev/null'
	  sh command   
	}

	inputFile = readFile 'servicenow_result.json'
    inputJSON = jsonParse(inputFile)
    sh 'rm servicenow_result.json'
    
    return inputJSON
}

def SNUpdate_CI(def sys_id, def sn_endpoint, def data_packet, def sn_params){

	withCredentials([[$class: 'UsernamePasswordBinding', credentialsId:SN_CRED_ID, variable: 'SN_CREDS']]){
	  writeFile file:'sn_data_packet.json' , text:data_packet
      command='curl -H  "Content-Type:application/json" -X PUT -T sn_data_packet.json -u ${SN_CREDS} "'+SN_API_URL+ sn_endpoint+'/'+sys_id+'?'+ sn_params.join('&')+'" 2>/dev/null'
      sh command
      sh 'rm sn_data_packet.json'
	}
}

def SNGET_CI_RELS(def sys_id,def sn_endpoint){
    def sn_params = ["sysparm_query=parent="+sys_id+"%5EORchild="+sys_id,
    "sysparm_display_value=true"]

	withCredentials([[$class: 'UsernamePasswordBinding', credentialsId:SN_CRED_ID, variable: 'SN_CREDS']]){
	  command='curl -f -X GET -H  "Content-Type:application/json" --user ${SN_CREDS} "'+SN_API_URL+sn_endpoint+'?'+sn_params.join('&')+'" > servicenow_result.json 2>/dev/null'
	  sh command   
	}

	inputFile = readFile 'servicenow_result.json'
    inputJSON = jsonParse(inputFile)
    sh 'rm servicenow_result.json'

    return inputJSON
}

def SNDelete_CI_Rel(def sys_id,def sn_endpoint){
	withCredentials([[$class: 'UsernamePasswordBinding', credentialsId:SN_CRED_ID, variable: 'SN_CREDS']]){
	  command='curl -f -X DELETE -H  "Content-Type:application/json" --user ${SN_CREDS} "'+SN_API_URL+sn_endpoint+'/'+sys_id+'" 2>/dev/null'
	  sh command   
	}
}

def SNDelete_CI(def sys_id){
  // Not going to get implemented according to the requirement of Service now Team
}

/*-------------------Test Functions------------------*/
def test_SNGET_CI_RELS(){
	def sn_sys_id = "c869d1b9dbc1e6009df6f4eabf961968"
	def sn_ci_endpoint =  "cmdb_rel_ci"
	println SNGET_CI_RELS(sn_sys_id,sn_ci_endpoint)
}

def test_SNGET_CI(){
	def sn_sys_id = "9cc098e8db492600bc7bff78bf961958"
	def sn_ci_endpoint =  "cmdb_ci_appl"
	println SNGET_CI(sn_sys_id,sn_ci_endpoint)
}

def test_UpdateCI(){
	def sn_data_packet = '''{
				"version":"2.9",
				"u_manifest_location":"somelocation.com",
				"u_source_code_location": "AWS"
				}'''
	def sn_sys_id = "9cc098e8db492600bc7bff78bf961958"
	def sn_ci_endpoint =  "cmdb_ci_appl"
	def sn_params = ["sysparm_display_value=true"]
	SNUpdate_CI(sn_sys_id,sn_ci_endpoint,sn_data_packet,sn_params)
}

def test_Update_CI_RELS(){
	def sn_data_packet = '''{
				"parent":"JS_DEMO_APP_Sanjaya",
				"child":"JS_DEMO_APP_test4",
				"type": "Depends on::Used by"
				}'''
	def sn_sys_id = "6c6915b9dbc1e6009df6f4eabf96196d"
	def sn_ci_endpoint =  "cmdb_rel_ci"
	def sn_params = ["sysparm_display_value=true"]

	SNUpdate_CI(sn_sys_id,sn_ci_endpoint,sn_data_packet,sn_params)
}

def test_SNDelete_CI_Rel(){
  def sn_sys_id = "9869d171db4d6600bc7bff78bf9619d6"
  def sn_ci_endpoint =  "cmdb_rel_ci"
  SNDelete_CI_Rel(sn_sys_id,sn_ci_endpoint)
 
}

/*---------------------------------------------------*/
node{
	ws{
		stage 'Update CI'
		SN_APPLICTION = "JS_DEMO_APP_test2"
		SN_CRED_ID="9ebcfa36-cb80-478b-8427-f102f954dc0b"
		SN_API_URL = "https://prokarmademo2.service-now.com/api/now/table/"

		test_Update_CI_RELS()
		//test_SNDelete_CI_Rel()
		//test_SNGET_CI_RELS()

	}
}