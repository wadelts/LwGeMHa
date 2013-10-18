/*
 * Validation of form fields:
 * 
 * 1)
 * Fields with class GeMHaToBeValidated will be validated at each change of the main
 * Select lists and whenever data is entered in the field itself.
 * 
 * 2)
 * Fields with class locallyRequired will have a validation function attached to them
 * while their enclosing "div" is displayed. This function will be triggered by keyup
 * events and will simply ensure they are not empty.
 * While their enclosing "div" is not displayed, they will be assumed to be valid, to
 * allow the Generate button to be enabled.
 * 
 * 3)
 * Fields with both classes locallyRequired AND GeMHaToBeValidated will be excluded from
 * item 2 above. The validation executed in item 1 will already include checks for locally
 * required.
 * 
 * 4)
 * Fields with either class, locallyRequired or GeMHaToBeValidated, will be included when
 * checking that all relevant fields are valid, allowing the Generate button to be enabled. 
 * 
 */

/*
 * Check to see if field is numeric.
 * Source: CMS @ http://stackoverflow.com/questions/18082/validate-numbers-in-javascript-isnumeric
 * 
 */
function isNumber(n) {
  return !isNaN(parseFloat(n)) && isFinite(n);
}

/*
 * Check to see if list is empty.
 * 
 */
function isEmpty(listJQ) {
  return ($(listJQ).find(' li').length == 0)
}

/*
 * Set all "validate-able" fields to red or green
 * 
 */
function validateAll() {
	$("input[type=text].GeMHaToBeValidated").each(validateNumericValForElement);
	
	checkCanGenerate();
}

/*
 * Check to see if which fields are valid on loading the form (ie pre-filled fields).
 * 
 * If all fields are valid, return true and enable Send button
 * Otherwise, disable Send button
 * 
 * 
 */
function validateInvariantsQueueInput() {
	
	checkCanGenerate();
}


/*
 * Check to see if we can send a mail - all fields must be valid.
 * 
 * If all fields are valid, return true and enable Send button
 * Otherwise, disable Send button
 * 
 * 
 */
function checkCanGenerate() {
	
	var generateButton = document.getElementById("generate");
	generateButton.disabled = ! allFieldsValid();
}

/*
 * Pumps through a JQuery object's id to validateNumericVal()
 * (The 'this' refers to the Element being process by the JQuery each() function,
 * as the callback is fired in the context of the current DOM element.)
 * 
 */
function validateNumericValForElement(index) {
	validateNumericVal(this.id);
}

/*
 * Validate Numerical value field, marking as valid if so.
 * 
 * 
 */
function validateNumericVal(fieldId) {
	var field = document.getElementById(fieldId);
	var hidden = ( $('#' + fieldId).parents('div:eq(0)').css('display') == 'none' );
	

	if (hidden) { // then just pretend field is valid - don't actually care
		field.setAttribute("GeMHa_Field_isValid", "true");
		return;
	} else { // If field's section (div) is displayed AND field is required, make sure not empty
		var locallyRequired = $('#' + fieldId).hasClass('locallyRequired');
		if (locallyRequired && field.value.length === 0) {
			field.setAttribute("GeMHa_Field_isValid", "false");
			return;
		}
	}

	// If got here, field is not hidden nor illegally empty
	if ( field.value.length === 0 || isNumber(field.value)) {
		field.setAttribute("GeMHa_Field_isValid", "true");
	} else {
		field.setAttribute("GeMHa_Field_isValid", "false");
	}
}

/*
 * Validate a value field that must not be empty, marking as valid if so.
 * Called by OnKeyUp events.
 * 
 * 
 */
function validateRequiredValField(event) {
    var field;
    if (event.target) {
    	field = event.target;
    } else if (event.srcElement) { // For IE
    	field = event.srcElement;
    } else {
    	return; // Won't work anyway
    }

    if ( field.value == "") {
    	field.setAttribute("GeMHa_Field_isValid", "false");
	} else {
		field.setAttribute("GeMHa_Field_isValid", "true");
	}
	
	checkCanGenerate();
}

/*
 * Validate a list (ul or ol) field that must not be empty, marking as valid if so.
 * 
 * 
 */
function validateRequiredList(listJQ) {
	var parentDiv = $(listJQ).parent('div:eq(0)');
	
	if ( isEmpty(listJQ) ) {
		$(parentDiv).css('border-color', '#ff0000');
		$(listJQ).attr("GeMHa_Field_isValid", "false");
	} else {
		$(parentDiv).css('border-color', '');
		$(listJQ).attr("GeMHa_Field_isValid", "true");
	}
	
	checkCanGenerate();
}

/*
 * Return true if given field has been marked as valid.
 * 
 */
function fieldIsValid(fieldId) {
	return ( $("#" + fieldId).attr("GeMHa_Field_isValid") == "true" )
}

/*
 * Check validity of all fields.
 * 
 * 
 */
function allFieldsValid() {
	var allValid = true;
	$(".GeMHaToBeValidated, .locallyRequired").each(
			function() {
				if ( ! fieldIsValid(this.id)) {
					allValid = false;
					return; // break out of traversal
				}
			} );

	return allValid;
}
