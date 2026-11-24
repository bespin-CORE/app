/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.account.AppUserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * Lets a signed-in user manage their own owner record and pets. No URL here carries an
 * owner id: the owner is always the one linked to the signed-in account, so there is no
 * way to address somebody else's record. The staff pages under {@code /owners} stay
 * separate.
 */
@Controller
@RequestMapping("/my")
class MyProfileController {

	private static final String VIEWS_PROFILE = "my/profile";

	private static final String VIEWS_OWNER_FORM = "owners/createOrUpdateOwnerForm";

	private static final String VIEWS_PET_FORM = "pets/createOrUpdatePetForm";

	private static final String REDIRECT_PROFILE = "redirect:/my/profile";

	private static final String REDIRECT_PROFILE_FORM = "redirect:/my/profile/edit";

	private final OwnerRepository owners;

	private final AppUserRepository users;

	private final PetTypeRepository types;

	MyProfileController(OwnerRepository owners, AppUserRepository users, PetTypeRepository types) {
		this.owners = owners;
		this.users = users;
		this.types = types;
	}

	/**
	 * Form values are bound to a fresh object and copied over field by field, so only
	 * these five fields can ever be set from a request.
	 */
	@InitBinder("owner")
	void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setAllowedFields("firstName", "lastName", "address", "city", "telephone");
	}

	@InitBinder("pet")
	void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
		dataBinder.setAllowedFields("name", "birthDate", "type");
	}

	private Optional<Owner> currentOwner(Principal principal) {
		return this.owners.findByAccountUsername(principal.getName());
	}

	@GetMapping("/profile")
	String showProfile(Principal principal, Model model) {
		Optional<Owner> owner = currentOwner(principal);
		if (owner.isEmpty()) {
			return REDIRECT_PROFILE_FORM;
		}
		model.addAttribute("owner", owner.get());
		return VIEWS_PROFILE;
	}

	@GetMapping("/profile/edit")
	String initProfileForm(Principal principal, Model model) {
		model.addAttribute("owner", currentOwner(principal).orElseGet(Owner::new));
		return VIEWS_OWNER_FORM;
	}

	@PostMapping("/profile/edit")
	String processProfileForm(@Valid @ModelAttribute("owner") Owner form, BindingResult result, Principal principal,
			RedirectAttributes redirectAttributes) {
		Optional<Owner> existing = currentOwner(principal);
		if (result.hasErrors()) {
			// Keeps the form in "update" wording when it is shown again
			existing.ifPresent(owner -> form.setId(owner.getId()));
			return VIEWS_OWNER_FORM;
		}

		Owner owner = existing.orElseGet(() -> {
			Owner created = new Owner();
			created.setAccount(this.users.findByUsername(principal.getName()).orElseThrow());
			return created;
		});
		owner.setFirstName(form.getFirstName());
		owner.setLastName(form.getLastName());
		owner.setAddress(form.getAddress());
		owner.setCity(form.getCity());
		owner.setTelephone(form.getTelephone());
		try {
			this.owners.saveAndFlush(owner);
		}
		catch (DataIntegrityViolationException ex) {
			if (existing.isPresent()) {
				throw ex;
			}
			// A second submission created the record for this account first
			return REDIRECT_PROFILE;
		}
		redirectAttributes.addFlashAttribute("messageKey", "myProfile.saved");
		return REDIRECT_PROFILE;
	}

	@GetMapping("/pets/new")
	String initPetCreationForm(Principal principal, Model model, RedirectAttributes redirectAttributes) {
		Optional<Owner> owner = currentOwner(principal);
		if (owner.isEmpty()) {
			return profileRequired(redirectAttributes);
		}
		return petForm(model, owner.get(), new Pet());
	}

	@PostMapping("/pets/new")
	String processPetCreationForm(@Valid @ModelAttribute("pet") Pet form, BindingResult result, Principal principal,
			Model model, RedirectAttributes redirectAttributes) {
		Optional<Owner> current = currentOwner(principal);
		if (current.isEmpty()) {
			return profileRequired(redirectAttributes);
		}
		Owner owner = current.get();
		rejectInvalidPet(owner, form, null, result);
		if (result.hasErrors()) {
			return petForm(model, owner, form);
		}

		// The form object only ever carries name, birth date and type, and has no id
		owner.addPet(form);
		return savePet(owner, form, result, model, redirectAttributes);
	}

	@GetMapping("/pets/{petId}/edit")
	String initPetUpdateForm(@PathVariable("petId") int petId, Principal principal, Model model,
			RedirectAttributes redirectAttributes) {
		Optional<Owner> owner = currentOwner(principal);
		if (owner.isEmpty()) {
			return profileRequired(redirectAttributes);
		}
		return petForm(model, owner.get(), ownPet(owner.get(), petId));
	}

	@PostMapping("/pets/{petId}/edit")
	String processPetUpdateForm(@PathVariable("petId") int petId, @Valid @ModelAttribute("pet") Pet form,
			BindingResult result, Principal principal, Model model, RedirectAttributes redirectAttributes) {
		Optional<Owner> current = currentOwner(principal);
		if (current.isEmpty()) {
			return profileRequired(redirectAttributes);
		}
		Owner owner = current.get();
		Pet pet = ownPet(owner, petId);
		// Keeps the form in "update" wording when it is shown again
		form.setId(petId);
		rejectInvalidPet(owner, form, petId, result);
		if (result.hasErrors()) {
			return petForm(model, owner, form);
		}

		pet.setName(form.getName());
		pet.setBirthDate(form.getBirthDate());
		pet.setType(form.getType());
		return savePet(owner, form, result, model, redirectAttributes);
	}

	private String profileRequired(RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("messageKey", "myProfile.required");
		return REDIRECT_PROFILE_FORM;
	}

	/**
	 * Looks a pet up among the signed-in user's own pets only, so a pet that belongs to
	 * someone else is as absent as one that does not exist.
	 */
	private Pet ownPet(Owner owner, int petId) {
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return pet;
	}

	private String petForm(Model model, Owner owner, Pet pet) {
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("types", this.types.findPetTypes());
		return VIEWS_PET_FORM;
	}

	/**
	 * The same rules as the staff pet form: a name is unique among one owner's pets and a
	 * birth date is not in the future.
	 */
	private void rejectInvalidPet(Owner owner, Pet form, Integer petId, BindingResult result) {
		if (StringUtils.hasText(form.getName())) {
			Pet sameName = owner.getPet(form.getName(), true);
			if (sameName != null && !sameName.getId().equals(petId)) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}
		if (form.getBirthDate() != null && form.getBirthDate().isAfter(LocalDate.now())) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}
	}

	private String savePet(Owner owner, Pet form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			this.owners.saveAndFlush(owner);
		}
		catch (DataIntegrityViolationException ex) {
			String message = ex.getMessage();
			if (message == null || !message.toLowerCase().contains("unique_owner_pet_name")) {
				throw ex;
			}
			// Another request saved a pet with this name first
			result.rejectValue("name", "duplicate", "already exists");
			return petForm(model, owner, form);
		}
		redirectAttributes.addFlashAttribute("messageKey", "myPet.saved");
		return REDIRECT_PROFILE;
	}

}
