/*
 * Optional section handling:
 * When an option is selected from a drop-down list, it triggers the following:
 * 		All sections bar that chosen are hidden
 * 
 * 		For the hidden sections, a keyup listener is removed for all validated input(text) fields
 * 		For the hidden sections, all validated input(text) and list(ol/ul) fields are set to valid
 * 
 * 		For the selected section, a keyup listener is added for all validated input(text) fields
 * 
 */

/*
 * Constructor function for new FilePath objects.
 * 
 */
function FilePath(path) {
	this.path = path;

	if (path.indexOf('/') >= 0)
		this.separator = "/";
	else
		this.separator = "\\";
}

/*
 * Prototype from which all FilePath objects will inherit.
 * 
 */
FilePath.prototype = {
	// Return path excluding the file name (and minus the separator)
	pathToParent: function() {
		var endPathIndex = this.path.lastIndexOf(this.separator);
		return this.path.substr(0, endPathIndex);
	},
	// Return the file name only
	fileName: function() {
		return this.path.split(this.separator).pop();
	}
}

// Got this from http://msdn.microsoft.com/en-us/library/ms537509(v=vs.85).aspx
// Returns the version of Internet Explorer or a -1 (indicating the use of another browser).
function getInternetExplorerVersion() {
	var rv = -1; // Return value assumes failure.
	if (navigator.appName == 'Microsoft Internet Explorer')
	{
		 var ua = navigator.userAgent;
		 var re  = new RegExp("MSIE ([0-9]{1,}[\.0-9]{0,})");
		 if (re.exec(ua) != null)
			 rv = parseFloat( RegExp.$1 );
	}

	return rv;
}

function showXMLInWindow() {
	if (getInternetExplorerVersion() >= 0) { // then is IE
		// Got this from http://stackoverflow.com/questions/11016998/javascript-render-dynamically-generated-xml-in-browser
		var newWindow = window.open('','_blank','toolbar=0, location=0, directories=0, status=0, scrollbars=1, resizable=1, copyhistory=1, menuBar=1, width=640, height=480, left=50, top=50', true);
		var preEl = newWindow.document.createElement("pre");
		var codeEl = newWindow.document.createElement("code");
		codeEl.appendChild(newWindow.document.createTextNode(formDataToXML()));
		preEl.appendChild(codeEl);
		newWindow.document.body.appendChild(preEl);
	} else {
		window.open('data:text/xml,' + encodeURIComponent(formDataToXML()))
	}
}

/*
 * Fill a template...
 * 
 */
function changeTemplate() {
	var dropDownList = document.getElementById('template');
	var selectedTemplate = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedTemplate !== "none") {
		// Call function defined by selected option of the select element
		window[selectedTemplate]();
	} 
}

/*
 * Display the Output section according to User's Output Medium selection..
 * 
 */
function changeInputDataContractChecking() {
	var dropDownList = document.getElementById('inputDataContractChecking');
	var selectedOutputMedium = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedOutputMedium == "none") {
		$('div#inputDataContractCheckingDiv').hide();
	} else {
		$('div#inputDataContractCheckingDiv').show();
	} 
}

/*
 * Show section according to User's Input Medium selection and
 * hide others in same section.
 * 
 * But exclude fields already being validated, e.g. numeric (class=GeMHaToBeValidated)
 * 
 */
function showSelectedMediaSection(selectedDiv, mediaClassName) {
	var selected = $('.' + mediaClassName + ' div#' + selectedDiv);
	var allDivs = $('.' + mediaClassName + ' div');
	
	$(allDivs).not(selected).hide();
	$(allDivs).not(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').unbind("keyup", validateRequiredValField);
	$(allDivs).not(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').attr("GeMHa_Field_isValid", "true"); // pretend is valid while hidden

	$(selected).show();
	$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').keyup(validateRequiredValField);
	$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').keyup(); // force immediate validation	
}

/*
 * Display the Input section according to User's Input Medium selection..
 * 
 */
function changeInputMedium() {
	var dropDownList = document.getElementById('inputMedium');
	var selectedInputMedium = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedInputMedium == "file") {
		showSelectedMediaSection("inputFileDiv", "inputMedia");
		changeInputFormat(); // For when Input Format already changed, before re-selecting file
	} else if (selectedInputMedium == "queue") {
		showSelectedMediaSection("inputQueueDiv", "inputMedia");
	} else if (selectedInputMedium == "socket") {
		showSelectedMediaSection("inputSocketDiv", "inputMedia");
	}
	
	validateAll();
}
/*
 * Display the CSV File section according to User's Input Format selection..
 * 
 */
function changeInputFormat() {
	
	var dropDownList = document.getElementById('inputFormat');
	var selectedInputFormat = dropDownList.options[dropDownList.selectedIndex].value;
	
	var dropDownList = document.getElementById('inputMedium');
	var selectedInputMedium = dropDownList.options[dropDownList.selectedIndex].value;

	var selected = $('div#inputFileCSV');
	if (selectedInputMedium == "file" && selectedInputFormat == "csv") {
		$(selected).show();
		$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').keyup(validateRequiredValField);
		$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').keyup(); // force immediate validation
		
		validateRequiredList($('#columnOrderList'));
		
		// The following problem appeared (I think) when I changed div to initially have style display:none instead of hidden!!!!
		// But even changing back to hidden, prob remains.
		$('div#columnOrderDiv').show(); // had to put this in, otherwise this div kept ending up with style="display:none" !!!
		$('div#columnOrderListButtons').show(); // had to put this in, otherwise this div kept ending up with style="display:none" !!!
	} else {
		$(selected).hide();
		$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').unbind("keyup", validateRequiredValField);
		$(selected).find('input[type=text].locallyRequired').not('.GeMHaToBeValidated').attr("GeMHa_Field_isValid", "true"); // pretend is valid while hidden
		$(selected).find('ol.locallyRequired').not('.GeMHaToBeValidated').attr("GeMHa_Field_isValid", "true"); // pretend is valid while hidden
		checkCanGenerate();
   	}
}

 /*
 * Display the CSV INSERT Params section according to User's Target XML Format selection..
 * 
 */
function changeTargetXMLFormat() {
	var dropDownList = document.getElementById('inputTargetXMLFormat');
	var selectedTargetXMLFormat = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedTargetXMLFormat == "insert") {
		$('div#inputFileCSVInsertDiv').show();
	} else {
		$('div#inputFileCSVInsertDiv').hide();
	}
}

/*
 * Disable Processing sections if "doing nothing" but outputting.
 * 
 */
function changeProcessingTarget() {
	var dropDownList = document.getElementById('processingTarget');
	var selectedProcessingTarget = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedProcessingTarget == "gemha.servers.ProcessMessageDoNothing") {
		$('fieldset.processingElements').hide();
		$('#sendElementList').attr("GeMHa_Field_isValid", "true"); // pretend is valid while hidden
	} else {
		$('fieldset.processingElements').show();
		validateRequiredList($('#sendElementList'));
	}
	
	checkCanGenerate();
}

/*
 * Display the Output section according to User's Output Medium selection..
 * 
 */
function changeOutputMedium() {
	var dropDownList = document.getElementById('outputMedium');
	var selectedOutputMedium = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedOutputMedium == "none") {
		showSelectedMediaSection("none", "outputMedia"); // There actually is no "none" section, so hides all
	} else if (selectedOutputMedium == "file") {
		showSelectedMediaSection("outputFileDiv", "outputMedia");
	} else if (selectedOutputMedium == "queue") {
		showSelectedMediaSection("outputQueueDiv", "outputMedia");
	} else if (selectedOutputMedium == "HTTP") {
		showSelectedMediaSection("outputHTTPDiv", "outputMedia");
	}
	
	checkCanGenerate();
}

/*
 * Disable Auditing sections if no auditing required.
 * 
 */
function changeAuditing() {
	var dropDownList = document.getElementById('auditing');
	var selectedAuditing = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedAuditing == "no") {
		$('div#auditingDiv').hide();
		$('#auditElementList').attr("GeMHa_Field_isValid", "true"); // pretend is valid while hidden
	} else {
		$('div#auditingDiv').show();
		validateRequiredList($('#auditElementList'));
	}
	
	checkCanGenerate();
}

/*
 * Ensure conflicting logging levels not selected...
 * 
 */
function changeEnvironmentLoggingLevel() {
	var dropDownList = document.getElementById('environmentLoggingLevel');
	var selectedEnvironmentLoggingLevel = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectedEnvironmentLoggingLevel != "Ignore") {
		$('#generalLoggingLevel').val("Ignore");
	} 
}

/*
 * Ensure conflicting logging levels not selected...
 * 
 */
function changeGeneralLoggingLevel() {
	var dropDownList = document.getElementById('environmentLoggingLevel');
	var selectGeneralLoggingLevel = dropDownList.options[dropDownList.selectedIndex].value;
	
	if (selectGeneralLoggingLevel != "Ignore") {
		$('#environmentLoggingLevel').val("Ignore");
	} 
}

/*
 * (Re)Set reasonable defaults.
 * 
 */
function setDefaults(replaceExisting) {
	$("#sortFilteredFileNames").prop('checked', true);

	var inputFieldSeparator = $("#inputFieldSeparator");
	if ($(inputFieldSeparator).val().length <= 0 || replaceExisting) {
		$(inputFieldSeparator).val("\\t");
	}
	
	var inputMilliSecondsBeforeQuiet = $("#inputMilliSecondsBeforeQuiet");
	if ($(inputMilliSecondsBeforeQuiet).val().length <= 0 || replaceExisting) {
		$(inputMilliSecondsBeforeQuiet).val("3000");
	}
	
}

/*
 * Build the XML document as a string, collecting the data from the main form.
 * 
 */
function formDataToXML() {
	var theXML = '<?xml version="1.0" encoding="utf-8"?><Applic>';
	theXML += "<Input>";
	theXML += "<InputSource>";
		theXML += formInputFieldToXML("InputLimit");
		theXML += formSelectFieldToXML("DataFormat", false);
		
		var inputDataContractChecking = $("#inputDataContractChecking").find(" :selected").val();
		if (inputDataContractChecking != "none") {
			theXML += "<DataContractName Location='" + $("#inputDataContractLocation").val() + "' ActionOnError='" + inputDataContractChecking + "'>" + 
					  $("#inputDataContractName").val() + 
					  "</DataContractName>";
		}
		
		var inputMedium = $("#inputMedium").find(" :selected").val();
		if (inputMedium == "file") {
			theXML += "<InputFile>";
				// Split out path and file name (filter)...
				var filePath = new FilePath($("#inputFileName").val());
				
				theXML += "<FileDir>" + filePath.pathToParent() + "</FileDir>";
				theXML += "<FileNameFilter>" + filePath.fileName() + "</FileNameFilter>";
				
				theXML += formInputFieldToXML("SortFilteredFileNames");
				
				var inputFormat = $("#inputFormat").find(" :selected").val();
				if (inputFormat == "csv") {
					theXML += "<CSVParams>";
						theXML += formInputFieldToXML("FieldSeparator");
						theXML += formInputFieldToXML("NumRecordsToSkip");
						
						// Now collect the set defining the ordering of the Input columns
						var columnOrderList = $("#columnOrderList li");
						if (columnOrderList.length > 0) {
							theXML += "<ColumnOrder>";
								$(columnOrderList).each(function(index) {
									theXML += "<Column>" + $(this).text() + "</Column>";
								} );
							theXML += "</ColumnOrder>";
						}
						
						theXML += formSelectFieldToXML("XMLFormat", false);
						
						var inputTargetXMLFormat = $("#inputTargetXMLFormat").find(" :selected").val();
						if (inputTargetXMLFormat == "insert") {
							theXML += "<InsertParams>";
								theXML += formSelectFieldToXML("Action_On_Error", false);
								theXML += formInputFieldToXML("Prepared_Statement_Name");
								theXML += formInputFieldToXML("Immediate_Commit");
							theXML += "</InsertParams>";
						}
						
					theXML += "</CSVParams>";
				}
			theXML += "</InputFile>";
		} else if (inputMedium == "queue") {
			theXML += "<InputQueue>";
				theXML += formInputFieldToXML("UrlJMSserver");
				theXML += formInputFieldToXML("QueueName");
				theXML += formInputFieldToXML("MilliSecondsBeforeQuiet");
			theXML += "</InputQueue>";
		} else if (inputMedium == "socket") {
			theXML += "<InputSocket>";
				theXML += formInputFieldToXML("PortNumber");
			theXML += "</InputSocket>";
		}
	theXML += "</InputSource>";
	
		var inputSchemaDefFileName = $("#inputSchemaDefFileName").val();
		if (inputSchemaDefFileName != "") {
			theXML += "<InputValidation SchemaValidation='on' SchemaDefinitionFileName='" + inputSchemaDefFileName + "'>" + 
					  "</InputValidation>";
		}

	theXML += "</Input>";
	
	var outputMedium = $("#outputMedium").find(" :selected").val();
	if (outputMedium != "none") {
		theXML += "<Output>";
		
		if (outputMedium == "queue") {
			theXML += "<OutputQueue>";
				theXML += formInputFieldToXML("UrlJMSserver", "div#outputQueueDiv");
				theXML += formInputFieldToXML("QueueName", "div#outputQueueDiv");
				theXML += formInputFieldToXML("ReplyToQueueName");
			theXML += "</OutputQueue>";
		} else if (outputMedium == "file") {
			theXML += "<OutputFile>";
				theXML += formInputFieldToXML("FileNameTemplate");
			theXML += "</OutputFile>";
		} else if (outputMedium == "HTTP") {
			theXML += "<OutputHTTP>";
				theXML += formInputFieldToXML("ServerUrl");
				theXML += formInputFieldToXML("EndPointName");
				theXML += formInputFieldToXML("HTTPWithBackoff");
			theXML += "</OutputHTTP>";			
		}

		theXML += "</Output>";
	}
	
	theXML += "<Processing>";
		theXML += formSelectFieldToXML("MessageProcessingClassName");
		var processingTarget = $("#processingTarget").find(" :selected").val();
		if (processingTarget != "gemha.servers.ProcessMessageDoNothing") {
			theXML += formInputFieldToXML("MessageProcessingSettingsFileName");
			
			// Collect the set "Mind" elements, if exists - these are the elements from the
			// input message that are not needed for processing but must be included in any
			// response (in the output section).
			var mindElementList = $("#mindElementList li");
			if (mindElementList.length > 0) {
				theXML += "<MindElements>";
					$(mindElementList).each(function(index) {
						theXML += "<ElementName>" + $(this).text() + "</ElementName>";
					} );
				theXML += "</MindElements>";
			}

			// Collect the set "Send" elements - these are the elements from the
			// input message that are needed for processing.
			var sendElementList = $("#sendElementList li");
			if (sendElementList.length > 0) {
				theXML += "<SendElements>";
					$(sendElementList).each(function(index) {
						theXML += "<ElementName>" + $(this).text() + "</ElementName>";
					} );
				theXML += "</SendElements>";
			}
		}
	
	theXML += "</Processing>";

	var auditing = $("#auditing").find(" :selected").val();
	if (auditing === "yes") {
		theXML += "<Auditing>";
			// Collect the set "Send" elements - these are the elements from the
			// input message that are needed for processing.
			var auditElementList = $("#auditElementList li");
			if (auditElementList.length > 0) {
				var auditingActionOnError = $("#actionOnError").find(" :selected").val();
				if (auditingActionOnError == "none") {
					theXML += "<AuditKeys>";					
				} else {
					theXML += "<AuditKeys ActionOnError='" + auditingActionOnError + "'>";
				}
				
					$(auditElementList).each(function(index) {
						theXML += "<KeyName>" + $(this).text() + "</KeyName>";
					} );
				theXML += "</AuditKeys>";
			}
			
			theXML += formInputFieldToXML("AuditKeysSeparator");

			var errorFileName = $("#errorFileName").val();
			if (errorFileName !== "") {
				theXML += "<ErrorFiles>";
					// Split out path and file name...
					var filePath = new FilePath(errorFileName);
					
					theXML += "<ErrorFilesDir>" + filePath.pathToParent() + "</ErrorFilesDir>";
					theXML += "<ErrorFileNameTemplate>" + filePath.fileName() + "</ErrorFileNameTemplate>";
				theXML += "</ErrorFiles>";
			}
		theXML += "</Auditing>";
	}
	
		theXML += "<Logging>";
			var environmentLoggingLevel = $("#environmentLoggingLevel").find(" :selected").val();
			if (environmentLoggingLevel != "Ignore") {
				theXML += "<Level>";
					theXML += "<EnvironmentLoggingLevel>" + environmentLoggingLevel + "</EnvironmentLoggingLevel>";
				theXML += "</Level>";
			} else {
				var generalLoggingLevel = $("#generalLoggingLevel").find(" :selected").val();
				if (generalLoggingLevel != "Ignore") {
					theXML += "<Level>";
						theXML += "<GeneralLoggingLevel>" + generalLoggingLevel + "</GeneralLoggingLevel>";
					theXML += "</Level>";
				}
			}
			
			theXML += formInputFieldToXML("LogFileDir");
			theXML += formInputFieldToXML("LogFileNameTemplate");
			theXML += formInputFieldToXML("ShutDownLogFileNameTemplate");
		theXML += "</Logging>";
		
	theXML += "</Applic>";
	
	return theXML;
}

/*
 * Based on the "name", return the value of an input field wrapped in XML.
 * 
 */
function formInputFieldToXML(fieldName, withinElementId) {
	withinElementId = withinElementId == undefined ? "" : withinElementId;

	var theField = $("#configForm " + withinElementId + " input[name=" + fieldName + "]");

	if ($(theField).is(":checkbox")) {
		var theValue = $("#sortFilteredFileNames").prop('checked');
	} else {
		var theValue = $(theField).val();
	}
	if (theValue != "")
		return "<" + fieldName + ">" + theValue + "</" + fieldName + ">";
	else
		return "";
}

/*
 * Based on the "name", return the value of a select field wrapped in XML.
 * 
 */
function formSelectFieldToXML(fieldName, isAttrib) {
	var theField = $("#configForm select[name=" + fieldName + "]");
	
	if (isAttrib) 
		return fieldName + "='" + $(theField).find(" :selected").val() + "' ";
	else
		return "<" + fieldName + ">" + $(theField).find(" :selected").val() + "</" + fieldName + ">";

}

/*
 * Fill the form with suggested data based on the following criteria:
 * 		Input: 		CSV file
 * 		Processing: None
 * 		Output: XML file
 * 
 */
function createTemplateCtoF() {
	///////////////////////////////////////////////////////////////////////////
	// Input settings...
	///////////////////////////////////////////////////////////////////////////
	$("#inputFormat").val("csv");
	
	$("#inputMedium").val("file");
	
	$("#inputFileName").val("/home/wade/gemha/data/input_CSV_[0-9]*.txt");
	$("#sortFilteredFileNames").prop('checked', true);
	$("#inputFieldSeparator").val("\\t");
	$("#inputNumRecordsToSkip").val("0");
	
	$("#inputTargetXMLFormat").val("insert");
	
	$("#inputActionOnError").val("exception");
	$("#inputPreparedStatementName").val("");
	$("#immediateCommit").prop('checked', false);
	
	// Simulate adding input column names (means all listeners are also added correctly)
	$("#columnOrderList").empty();
	var inputNewColName = $("#inputNewColName");
	var columnOrderListAdd = $("#columnOrderListAdd");
	$(inputNewColName).val("UserId");
	$(columnOrderListAdd).click(); // trigger('click') didn't trigger the event listeners
	$(inputNewColName).val("UserName");
	$(columnOrderListAdd).click();

	$("#inputFormat").trigger('change');
	$("#inputMedium").trigger('change');
	$("#inputTargetXMLFormat").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Processing settings
	///////////////////////////////////////////////////////////////////////////
	$("#processingTarget").val("gemha.servers.ProcessMessageDoNothing");

	///////////////////////////////////////////////////////////////////////////
	// Output settings
	///////////////////////////////////////////////////////////////////////////
	$("#outputMedium").val("file");

	$("#outputFileName").val("/home/wade/gemha/output/response_*_?.xml");
	

	// Auditing settings
	$("#actionOnError").val("shutdown");
	$("#auditKeysSeparator").val("-");
	$("#errorFileName").val("/home/wade/gemha/output/ErrorMessage_*_?.xml");
	
	// Simulate adding an Audit Key (means all listeners are also added correctly)
	$("#auditElementList").empty();
	var inputNewAuditElement = $("#inputNewAuditElement");
	var auditElementListAdd = $("#auditElementListAdd");
	$(inputNewAuditElement).val("/Message/DbAction/Insert/Columns/UserId");
	$(auditElementListAdd).click(); // trigger('click') didn't trigger the event listeners
	
	// Logging settings
	$("#environmentLoggingLevel").val("Development");
	$("#logFileDir").val("/home/wade/gemha/logs");
	$("#logFileNameTemplate").val("CtoF_001.log");
	$("#shutDownLogFileNameTemplate").val("CtoF_001_shutdown.log");

	$("#processingTarget").trigger('change');
	$("#outputMedium").trigger('change');
	$("#environmentLoggingLevel").trigger('change');
}

/*
 * Fill the form with suggested data based on the following criteria:
 * 		Input: 		CSV file
 * 		Processing: Database
 * 		Output: XML file
 * 
 */
function createTemplateCtoDbtoF() {
	///////////////////////////////////////////////////////////////////////////
	// Input settings...
	///////////////////////////////////////////////////////////////////////////
	$("#inputFormat").val("csv");
	
	$("#inputMedium").val("file");
	
	$("#inputFileName").val("/home/wade/gemha/data/insert_CSV_[0-9]*.txt");
	$("#sortFilteredFileNames").prop('checked', true);
	$("#inputFieldSeparator").val("\\t");
	$("#inputNumRecordsToSkip").val("0");
	
	$("#inputTargetXMLFormat").val("insert");
	
	$("#inputActionOnError").val("exception");
	$("#inputPreparedStatementName").val("insert_002");
	$("#immediateCommit").prop('checked', true);
	
	// Simulate adding input column names (means all listeners are also added correctly
	$("#columnOrderList").empty();
	var inputNewColName = $("#inputNewColName");
	var columnOrderListAdd = $("#columnOrderListAdd");
	$(inputNewColName).val("UserId");
	$(columnOrderListAdd).click(); // trigger('click') didn't trigger the event listeners
	$(inputNewColName).val("UserName");
	$(columnOrderListAdd).click();

	$("#inputFormat").trigger('change');
	$("#inputMedium").trigger('change');
	$("#inputTargetXMLFormat").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Processing settings
	///////////////////////////////////////////////////////////////////////////
	$("#processingTarget").val("gemha.servers.ProcessMessageForDb");

	$("#processingConfigFile").val("/home/wade/gemha/config/ProcessMessageForDb_002.xml");

	// Clear list (in case previous Template had populated it)
	$("#mindElementList").empty();

	// Simulate adding a Send Element (means all listeners are also added correctly)
	$("#sendElementList").empty();
	var inputNewSendElement = $("#inputNewSendElement");
	var sendElementListAdd = $("#sendElementListAdd");
	$(inputNewSendElement).val("/Message/DbAction");
	$(sendElementListAdd).click(); // trigger('click') didn't trigger the event listeners

	$("#processingTarget").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Output settings
	///////////////////////////////////////////////////////////////////////////
	$("#outputMedium").val("file");

	$("#outputFileName").val("/home/wade/gemha/output/Db_response_002_*_?.xml");
	
	// Auditing settings
	$("#actionOnError").val("shutdown");
	$("#auditKeysSeparator").val("-");
	$("#errorFileName").val("/home/wade/gemha/output/Db_ErrorMessage_002_*_?.xml");
	
	// Simulate adding an Audit Key (means all listeners are also added correctly)
	$("#auditElementList").empty();
	var inputNewAuditElement = $("#inputNewAuditElement");
	var auditElementListAdd = $("#auditElementListAdd");
	$(inputNewAuditElement).val("/Message/DbAction/Insert/Columns/UserId");
	$(auditElementListAdd).click(); // trigger('click') didn't trigger the event listeners
	
	// Logging settings
	$("#environmentLoggingLevel").val("Development");
	$("#logFileDir").val("/home/wade/gemha/logs");
	$("#logFileNameTemplate").val("CtoDbtoF_002.log");
	$("#shutDownLogFileNameTemplate").val("CtoDbtoF_002_shutdown.log");

	$("#outputMedium").trigger('change');
	$("#environmentLoggingLevel").trigger('change');
}

/*
 * Fill the form with suggested data based on the following criteria:
 * 		Input: 		XML file
 * 		Processing: CSV File
 * 		Output: 	Queue
 * 
 */
function createTemplateFtoFtoQ() {
	///////////////////////////////////////////////////////////////////////////
	// Input settings...
	///////////////////////////////////////////////////////////////////////////
	$("#inputFormat").val("xml");
	
	$("#inputMedium").val("file");
	
	$("#inputFileName").val("/home/wade/gemha/data/InputRecords_[0-9]*.xml");
	$("#sortFilteredFileNames").prop('checked', true);
	
	$("#inputFormat").trigger('change');
	$("#inputMedium").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Processing settings
	///////////////////////////////////////////////////////////////////////////
	$("#processingTarget").val("gemha.servers.ProcessMessageForFile");

	$("#processingConfigFile").val("/home/wade/gemha/config/ProcessMessageForFile_003.xml");

	// Clear list (in case previous Template had populated it)
	$("#mindElementList").empty();

	// Simulate adding a Send Element (means all listeners are also added correctly)
	$("#sendElementList").empty();
	var inputNewSendElement = $("#inputNewSendElement");
	var sendElementListAdd = $("#sendElementListAdd");
	$(inputNewSendElement).val("FileRequest");
	$(sendElementListAdd).click(); // trigger('click') didn't trigger the event listeners

	$("#processingTarget").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Output settings
	///////////////////////////////////////////////////////////////////////////
	$("#outputMedium").val("queue");

	$("#outputURLJMSServer").val("failover://tcp://localhost:61616");
	$("#outputQueueName").val("TransformationResultsQueue");
	
	// Auditing settings
	$("#actionOnError").val("shutdown");
	$("#auditKeysSeparator").val("-");
	$("#errorFileName").val("/home/wade/gemha/output/ErrorMessage_003_*_?.xml");
	
	// Simulate adding an Audit Key (means all listeners are also added correctly)
	$("#auditElementList").empty();
	var inputNewAuditElement = $("#inputNewAuditElement");
	var auditElementListAdd = $("#auditElementListAdd");
	$(inputNewAuditElement).val("FileRequest/Key");
	$(auditElementListAdd).click(); // trigger('click') didn't trigger the event listeners
	
	// Logging settings
	$("#environmentLoggingLevel").val("Development");
	$("#logFileDir").val("/home/wade/gemha/logs");
	$("#logFileNameTemplate").val("FtoFtoQ_003.log");
	$("#shutDownLogFileNameTemplate").val("FtoFtoQ_003_shutdown.log");

	$("#outputMedium").trigger('change');
	$("#environmentLoggingLevel").trigger('change');
}

/*
 * Fill the form with suggested data based on the following criteria:
 * 		Input: 		XML file
 * 		Processing: Socket
 * 		Output: 	Queue
 * 
 */
function createTemplateFtoStoQ() {
	///////////////////////////////////////////////////////////////////////////
	// Input settings...
	///////////////////////////////////////////////////////////////////////////
	$("#inputFormat").val("xml");
	
	$("#inputMedium").val("file");
	
	$("#inputFileName").val("/home/wade/gemha/data/InputRecords_[0-9]*.xml");
	$("#sortFilteredFileNames").prop('checked', true);
	
	$("#inputFormat").trigger('change');
	$("#inputMedium").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Processing settings
	///////////////////////////////////////////////////////////////////////////
	$("#processingTarget").val("gemha.servers.ProcessMessageForSocket");

	$("#processingConfigFile").val("/home/wade/gemha/config/ProcessMessageForSocket_004.xml");

	// Simulate adding a Mind Element (means all listeners are also added correctly)
	$("#mindElementList").empty();
	var inputNewMindElement = $("#inputNewMindElement");
	var mindElementListAdd = $("#mindElementListAdd");
	$(inputNewMindElement).val("FileRequest/DeliveryDetails");
	$(mindElementListAdd).click(); // trigger('click') didn't trigger the event listeners

	// Simulate adding a Send Element (means all listeners are also added correctly)
	$("#sendElementList").empty();
	var inputNewSendElement = $("#inputNewSendElement");
	var sendElementListAdd = $("#sendElementListAdd");
	$(inputNewSendElement).val("FileRequest/DbAction");
	$(sendElementListAdd).click(); // trigger('click') didn't trigger the event listeners

	$("#processingTarget").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Output settings
	///////////////////////////////////////////////////////////////////////////
	$("#outputMedium").val("queue");

	$("#outputURLJMSServer").val("mq://localhost:7676");
	$("#outputQueueName").val("SockServerResultsQueue");
	
	// Auditing settings
	$("#actionOnError").val("shutdown");
	$("#auditKeysSeparator").val("-");
	$("#errorFileName").val("/home/wade/gemha/output/SockServ_ErrorMessage_004_*_?.xml");
	
	// Simulate adding an Audit Key (means all listeners are also added correctly)
	$("#auditElementList").empty();
	var inputNewAuditElement = $("#inputNewAuditElement");
	var auditElementListAdd = $("#auditElementListAdd");
	$(inputNewAuditElement).val("FileRequest/DeliveryDetails/SomeKey");
	$(auditElementListAdd).click(); // trigger('click') didn't trigger the event listeners
	
	// Logging settings
	$("#environmentLoggingLevel").val("Development");
	$("#logFileDir").val("/home/wade/gemha/logs");
	$("#logFileNameTemplate").val("FtoStoQ_004.log");
	$("#shutDownLogFileNameTemplate").val("FtoStoQ_004_shutdown.log");

	$("#outputMedium").trigger('change');
	$("#environmentLoggingLevel").trigger('change');
}

/*
 * Fill the form with suggested data based on the following criteria:
 * 		Input: 		Socket
 * 		Processing: Database (SELECT)
 * 		Output: 	HTTP
 * 
 */
function createTemplateStoDbtoH() {
	///////////////////////////////////////////////////////////////////////////
	// Input settings...
	///////////////////////////////////////////////////////////////////////////
	$("#inputFormat").val("xml");
	
	$("#inputMedium").val("socket");
	
	$("#inputPortNumber").val("11819");
	
	$("#inputFormat").trigger('change');
	$("#inputMedium").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Processing settings
	///////////////////////////////////////////////////////////////////////////
	$("#processingTarget").val("gemha.servers.ProcessMessageForDb");

	$("#processingConfigFile").val("/home/wade/gemha/config/ProcessMessageForDb_005.xml");

	// Clear list (in case previous Template had populated it)
	$("#mindElementList").empty();

	// Simulate adding a Send Element (means all listeners are also added correctly)
	$("#sendElementList").empty();
	var inputNewSendElement = $("#inputNewSendElement");
	var sendElementListAdd = $("#sendElementListAdd");
	$(inputNewSendElement).val("/Message/DbAction");
	$(sendElementListAdd).click(); // trigger('click') didn't trigger the event listeners

	$("#processingTarget").trigger('change');
	///////////////////////////////////////////////////////////////////////////
	// Output settings
	///////////////////////////////////////////////////////////////////////////
	$("#outputMedium").val("HTTP");

	$("#outputHTTPServerUrl").val("http://localhost:8080");
	$("#outputEndpointName").val("RegisterSelectResponseServlet");
	$("#HTTPWithBackoff").prop('checked', true);
	
	// Auditing settings
	$("#actionOnError").val("shutdown");
	$("#auditKeysSeparator").val("-");
	$("#errorFileName").val("/home/wade/gemha/output/Db_ErrorMessage_005_*_?.xml");
	
	// Simulate adding an Audit Key (means all listeners are also added correctly)
	$("#auditElementList").empty();
	var inputNewAuditElement = $("#inputNewAuditElement");
	var auditElementListAdd = $("#auditElementListAdd");
	$(inputNewAuditElement).val("/Message/DbAction/Select/Where/UserId");
	$(auditElementListAdd).click(); // trigger('click') didn't trigger the event listeners
	
	// Logging settings
	$("#environmentLoggingLevel").val("Development");
	$("#logFileDir").val("/home/wade/gemha/logs");
	$("#logFileNameTemplate").val("StoDbtoH_005.log");
	$("#shutDownLogFileNameTemplate").val("StoDbtoH_005_shutdown.log");

	$("#outputMedium").trigger('change');
	$("#environmentLoggingLevel").trigger('change');
}
