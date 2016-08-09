
def SSCreateCI(def ss_ci_data, def endpoint){
  def SS_CREATE_CI_URL = SS_URL + "/api/now/table/" 

  withCredentials([[$class: 'UsernamePasswordBinding', credentialsId:SS_CRED_ID, variable: 'SN_CREDS']]){
    writeFile file:'sn_parent_ci_data.json', text:SS_PARENT_CI_DATA
    command='curl -v -H  "Content-Type:application/json" -X POST --data "'+ss_ci_data+'" -u ${SN_CREDS} "'+SS_CREATE_CI_URL+ endpoint+ '" 2>/dev/null'
    sh command
    sh 'rm sn_parent_ci_data.json'
  }
}

// This method will create a parent CI. End point and the Data will need to get declared at the global level
def SSCreateParentCI(){
	SSCreateCI(SS_PARENT_CI_DATA,SS_CREATE_CI_ENDPOINT)
}

// Creates the Parent child CI relation so that Parent CI will reflect all the dependancies with other CIs
// Any parent CI
def SSCreateChildCIRel(def ci_relations){
  def i = 0

  while (i < ci_relations.size()){
	SSCreateCI(ci_relations[i],SS_CI_REL_ENDPIONT)
	i++
  }
}


node{
	ws{
		stage 'Create CI'
		SS_APPLICTION = "JS_DEMO_APP_test99"
		SS_CRED_ID="9ebcfa36-cb80-478b-8427-f102f954dc0b"
		SS_URL = "https://prokarmademo2.service-now.com"
		SS_CREATE_CI_ENDPOINT = "cmdb_ci_appl"
		SS_CI_REL_ENDPIONT = "cmdb_rel_ci"
		SS_PARENT_CI_DATA = '{
							  "name":"'+SS_APPLICTION+'",
							  "version":"1.1.0",
							  "owned_by":"",
							  "support_group":"",
							  "operational_status":"",
							  "short_description":"This is just a test",
							  "u_manifest_location":"",
							  "u_source_code_location":""
							}'

		def SSChildCIList = []

		SS_CHILD_CI_DATA = '{"parent":"'+SS_APPLICTION+ '",
		"child":"*ANNIE-IBM",
        "type":"Depends on::Used by"
        }'
        
        SS_CHILD_CI_DATA2 = '{"parent":"'+SS_APPLICTION+ '",
		"child":"ApplicationServerPeopleSoft",
        "type":"Depends on::Used by"
        }'

        SS_CHILD_CI_DATA3 = '{"parent":"'+SS_APPLICTION+ '",
		"child":"ApplicationServerPeopleSoft",
        "type":"Depends on::Used by"
        }'

        SSChildCIList << SS_CHILD_CI_DATA

        SSChildCIList << SS_CHILD_CI_DATA2

        SSChildCIList << SS_CHILD_CI_DATA3

		SSCreateParentCI()
		SSCreateChildCIRel(SSChildCIList)
	}
}