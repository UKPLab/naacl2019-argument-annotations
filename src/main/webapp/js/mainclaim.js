/*
* Copyright 2019
* Ubiquitous Knowledge Processing (UKP) Lab
* Technische Universität Darmstadt
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

var IE = false;
$(document).ready(function() {
    IE = msieversion();
    $("#textbox").attr("disabled", "disabled");
    $("#button_submit").hide();
    $("#remove_claim").hide();
	$('#userAgent').val(navigator.userAgent);

    var el = document.getElementById('text_claim');
    $("#buttonGuideline").click(function(){
        $("#div_guideline").toggle()
    });
    $('#text_clear_button0').hide();
    console.log("ready");
    var sandbox = document.getElementById('text');
    $('#add_claim').hide();
    $('#textbox').attr('autocomplete','off').val("").bind("cut copy paste",function(e) {
        e.preventDefault();
    });

    $('input[name="intext"][value="true"]').prop('checked', true);

});


var lang = document.documentElement.lang;

var isDown = false;
$("body").mousedown(function() {
        isDown = true;      // When mouse goes down, set isDown to true
    })
    .mouseup(function() {
        isDown = false;    // When mouse goes up, set isDown to false
    });



var except = document.getElementById('button_submit');
var body = document.getElementById("wrapper");

body.addEventListener("mouseup", function () {
    highlighting();
}, false);
except.addEventListener("mouseup", function (ev) {
    ev.stopPropagation();
}, false);

// $("body").on("mouseup",function(){
//
//     highlighting()
// });

$('#text_clear_button0').click(function(){
    console.log("clear clicked");
    $("#button_submit").hide()
});
count = 0;



function highlighting(){
    var accepted = checkIfaccepted();
    if (!accepted){
        alert("accept the HIT!");

        $("#button_submit").hide();
        return false
    }
    var text = "";

    var selected = $("input[type='radio'][name='intext']:checked");
    if (selected.length > 0) {
        selectedVal = selected.val();
        if (selectedVal.indexOf("false") !== -1) {
            return false;
        }
    }

    var selection = window.getSelection();
    if (selection.toString() != "") {
        var range;
        if (!IE) {
            range = selection.getRangeAt(0);
        }
        else if (IE) {
            range = document.selection.createRange();
        }
        var allWithinRangeParent = range.commonAncestorContainer.getElementsByTagName("span");
        var allSelected = [];

        $(".highlighted_element").unwrap()
        $('span').removeClass("highlighted_element").removeClass("highlighted");


        for (var i = 0, el; el = allWithinRangeParent[i]; i++) {
            if (selection.containsNode(el, true)) {
                allSelected.push(el);
                var className = el.className;
                $(el).addClass("highlighted_element");

            }
        }
		if(allSelected.length == 0){
			return;
		}
        var isStart = true;
        var isEnd = false;
        for (var i = allSelected.length-1; i >= 0; i--){
            var el =  allSelected[i];
            var className = el.className;
            console.log(className);
            if (className == "token highlighted_element" && isStart) {
                console.log("i am here");
                isStart = false;
                console.log("first word "+ text)
            }
            else if (isStart && className !== "token highlighted_element") {
                $(el).removeClass("highlighted_element").removeClass('noselect');
                console.log("class removed " + el.id);
            }
            else if (i == 1 && className !== "token highlighted_element"){
                var next =  allSelected[0];
                var nextClassName = next.className;
                if (nextClassName !== "token highlighted_element"){
                    $(next).removeClass("highlighted_element").removeClass('noselect');
                    isEnd = true;
                    console.log("class removed " + el.id+" "+isEnd);
                }

            }

        }
		//Currently this checks for first token(s) being non-tokens (spaces or punctuations)
		//Iterates over all elements and removes them, till the first token is found
		for (var i = 0; i < allSelected.length; i++){
			var el =  allSelected[i];
			//$(el).addClass("element_"+ count);
			var className = el.className;
			console.log(className);
			
			if(className.indexOf("token") == -1){
				$(el).removeClass("highlighted_element").removeClass('noselect');
			}
			else{
				break;
			}
		}

    }
    else {
        $("#button_submit").hide();
    }

    selection.removeAllRanges();
    $("#button_submit").show()
    $('#text_clear_button0').show()
    if (typeof allSelected !== 'undefined') {
        for (var i = 0; i < allSelected.length; i++) {
            var el = allSelected[i];
            var classname = el.className;
            if (classname.indexOf("token") == -1) {
                console.log("classname " + classname);
                $(allSelected[i]).removeClass("highlighted_element").removeClass('noselect');
            }
            else {
                break;
            }

        }
		text = $("#highlight").value;
        $(".highlighted_element").wrapAll("<span class='highlighted' id='span_" + count + "' />")
        var highlighted = $(".highlighted").children();
        for (var i = 0; i < highlighted.length; i++) {
            if (i == 0)
                text = $(highlighted[i]).text()
            else
                text = text + "" + $(highlighted[i]).text()

        }
        console.log($(el).html() + " text " + text)
    }
    else {
        $('#button_submit').hide();
        $(".highlighted_element").unwrap();
        $(".highlighted_element").removeClass("highlighted_element");
        $(".highlighted").removeClass("highlighted");
        text = $("#highlight").val();

    }
    $("#infocus").html(text);

    count = count +1;

    console.log('All selected =', allSelected);


}

$( "#textbox" ).on('input', function() {
    if ( $("#textbox").val().length > 15 )
    {
        $("#button_submit").show();
    }
    else {
        $("#button_submit").hide();
    }

});

//var tokens = ""

$("#button_submit").click(function(){
    console.log("submit clicked")
    var tokens = [];
    var highlighted = document.getElementsByClassName("highlighted_element");

   for (var i = 0; i < highlighted.length; i++){
        el = highlighted[i]
        if (el.id != "")
            tokens.push(el.id)
    }
    console.log(highlighted.length);
    $("#textinput").val($("#textbox").val());
    $("#tokens").val(tokens.join(","));
 //   tokens = tokens.join(",")
});

counter = 0;
$('#add_claim').click(function(){
    if (counter > 1)
        $("#remove_claim").show();

    $(".highlighted").addClass('annotated'+counter).removeClass("highlighted").addClass('noselect');
    $(".highlighted_element").removeClass("highlighted_element").addClass("noselect").addClass("annotated"+counter+"_element");

    $('#remove_claim').show()
    document.getElementById("infocus").setAttribute("id", "claim"+counter);
    counter = counter+1;
    document.getSelection().removeAllRanges();
    console.log("add class " +'because_annotated'+counter)
    console.log("add "+counter)
	var editText = $("#editme").value;
    $("#divClaim").append("<p class=\"claim answers\" id=\"infocus\"><em>"+editText+"</em> </p>");
    $('#add_claim').hide()

});




$("span").hover(function(){
    if (isDown) {
        var classname = this.className;
        console.log(this.className)
        if (classname.indexOf("noselect") !== -1){
            console.log("found highlighted!");
            var selection = window.getSelection();
            selection.removeAllRanges();
        }
    }
});

$('#remove_claim').click(function(){
    counter = counter-1
    if (counter == 0) {
        $('#remove_claim').hide()
    }

    console.log("counter "+ counter)
    $('.highlighted').removeClass('highlighted');
    $('.highlighted_element').removeClass('highlighted_element');

    $('.annotated'+counter).removeClass('noselect').addClass('highlighted');
    $('.annotated'+counter+"_element").addClass("highlighted_element").removeClass("annotated"+counter+"_element").removeClass("noselect");



    $('#infocus').remove();
    document.getElementById("claim"+counter).setAttribute("id", "infocus");


    $('.clear_button').show()
    $('#add_because').show()
    $("#button_submit").show()
});


$("input[type=radio][name='intext']").change(function() {
    var selectedVal = "";
    var selected = $("input[type='radio'][name='intext']:checked");
    if (selected.length > 0) {
        selectedVal = selected.val();
        if (selectedVal.indexOf("true") !== -1){
            $("#button_submit").hide();
            if ($('.highlighted').length > 0) $("#button_submit").show();
            $("#textbox").attr("disabled", "disabled").removeClass("inputbox");
			$("#textbox").val("");
            $("#infocus").addClass("inputbox");
            $("body").removeClass("noselect");
            $(".claim").css("box-shadow", "0 0 5px rgba(81, 203, 238, 1)").css("color", "black");

        }
        else {
            $("#button_submit").hide();
            $("#textbox").removeAttr("disabled").addClass("inputbox");
            var selection = window.getSelection();
            selection.removeAllRanges();
            $('body').addClass('noselect');
            $(".claim").css("box-shadow", "0 0 5px grey").css("color", "grey");
            if ( $("#textbox").val().length > 15 )
            {
                $("#button_submit").show();
            }

        }
    }

});

function gup( name )
{
    var regexS = "[\\?&amp;]"+name+"=([^&amp;#]*)";
    var regex = new RegExp( regexS );
    var tmpURL = window.location.href;
    var results = regex.exec( tmpURL );
    if( results == null )
        return "";
    else
        return results[1];
}

function decode(strToDecode)
{
    var encoded = strToDecode;
    return unescape(encoded.replace(/\+/g,  " "));
}

function checkIfaccepted()
{
    msieversion();
    document.getElementById('assignmentId').value = gup('assignmentId');


    //
    // Check if the worker is PREVIEWING the HIT or if they've ACCEPTED the HIT
    //
    console.log("value is " + " "+ document.getElementById('assignmentId').value)

    if (gup('assignmentId') == "ASSIGNMENT_ID_NOT_AVAILABLE")
    {
        // If we're previewing, disable the button and give it a helpful message

        console.log(gup('assignmentId'));

        var inputs = document.getElementsByTagName("INPUT");
        for (var i = 0; i < inputs.length; i++) {
            console.log(inputs[i]);
            inputs[i].disabled = true;
        }


        document.getElementById('button_submit').disabled = true;
        document.getElementById('button_submit').value = "ACCEPT THE HIT";
        return false;
    } else {
        var form = document.getElementById('mturk_form');
        if (document.referrer && ( document.referrer.indexOf('workersandbox') != -1) ) {
            form.action = "https://workersandbox.mturk.com/mturk/externalSubmit";
        }
        return true;
    }
}

function msieversion()
{

    if(navigator.userAgent.indexOf('MSIE')!==-1
        || navigator.appVersion.indexOf('Trident/') > 0){
        alert("Internet Explorer is not supported. Please use another browser. \n" +
            "Internet Explorer wird nicht unterstützt. Bitte benutzen Sie einen anderen Browser. ");
        return true;
    }

    else  // If another browser, return 0
    {
        console.log("not IE "+ navigator.userAgent);
        return false;
    }

}
